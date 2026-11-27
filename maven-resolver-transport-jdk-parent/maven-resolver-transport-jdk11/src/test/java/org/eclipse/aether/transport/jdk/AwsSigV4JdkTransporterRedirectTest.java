/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.transport.jdk;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signed request that is redirected: a hop that stays on the repository origin is signed again for its own path,
 * a hop that leaves the origin carries no signature header at all. Two loopback servers on different ports stand
 * for the repository origin and a foreign one.
 */
class AwsSigV4JdkTransporterRedirectTest {
    private static final byte[] CONTENT = "content".getBytes(StandardCharsets.UTF_8);

    private HttpServer repository;

    private HttpServer foreign;

    /** Request headers (lower-cased names) per "port/path". */
    private final Map<String, Map<String, String>> seen = new ConcurrentHashMap<>();

    private HttpServer start(String redirectFrom, String redirectTo) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders()
                    .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
            seen.put(server.getAddress().getPort() + path, headers);
            if (path.equals(redirectFrom)) {
                exchange.getResponseHeaders().add("Location", redirectTo);
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(200, CONTENT.length);
                exchange.getResponseBody().write(CONTENT);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    @BeforeEach
    void startServers() throws IOException {
        foreign = start(null, null);
        String foreignUrl = "http://127.0.0.1:" + foreign.getAddress().getPort() + "/foreign/target.txt";
        // one repository server cannot redirect two paths with start(), so /repo/cross.txt is the only redirect
        // here and the same-origin one is served by a second repository context
        repository = start("/repo/cross.txt", foreignUrl);
        repository.createContext("/repo/same.txt", exchange -> {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders()
                    .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
            seen.put(repository.getAddress().getPort() + "/repo/same.txt", headers);
            exchange.getResponseHeaders().add("Location", "/repo/moved/same.txt");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    @AfterEach
    void stopServers() {
        repository.stop(0);
        foreign.stop(0);
    }

    private Transporter newTransporter() throws Exception {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(h -> true);
        session.setConfigProperty(
                ConfigurationProperties.HTTP_REQUEST_SIGNER, ConfigurationProperties.HTTP_REQUEST_SIGNER_AWS_SIGV4);
        session.setConfigProperty(ConfigurationProperties.HTTP_AWS_REGION, "us-east-1");
        RemoteRepository remote = new RemoteRepository.Builder(
                        "s3",
                        "default",
                        "http://127.0.0.1:" + repository.getAddress().getPort() + "/repo/")
                .setAuthentication(new AuthenticationBuilder()
                        .addUsername("access")
                        .addPassword("secret")
                        .build())
                .build();
        return new JdkTransporterFactory(s -> null, new PathProcessorSupport()).newInstance(session, remote);
    }

    @Test
    void sameOriginRedirectIsSignedAgainForTheNewPath() throws Exception {
        try (Transporter transporter = newTransporter()) {
            GetTask get = new GetTask(URI.create("same.txt"));
            transporter.get(get);
            assertArrayEquals(CONTENT, get.getDataBytes());
        }
        int port = repository.getAddress().getPort();
        Map<String, String> first = seen.get(port + "/repo/same.txt");
        Map<String, String> second = seen.get(port + "/repo/moved/same.txt");
        assertNotNull(first);
        assertNotNull(second, "the redirect was followed");
        assertTrue(first.get("authorization").startsWith("AWS4-HMAC-SHA256 Credential=access/"));
        assertTrue(second.get("authorization").startsWith("AWS4-HMAC-SHA256 Credential=access/"));
        assertNotEquals(first.get("authorization"), second.get("authorization"), "signature is for the new path");
        assertNotNull(second.get("x-amz-date"));
        assertNotNull(second.get("x-amz-content-sha256"));
    }

    @Test
    void crossOriginRedirectCarriesNoSignature() throws Exception {
        try (Transporter transporter = newTransporter()) {
            GetTask get = new GetTask(URI.create("cross.txt"));
            transporter.get(get);
            assertArrayEquals(CONTENT, get.getDataBytes());
        }
        assertNotNull(
                seen.get(repository.getAddress().getPort() + "/repo/cross.txt").get("authorization"));
        Map<String, String> foreignHeaders = seen.get(foreign.getAddress().getPort() + "/foreign/target.txt");
        assertNotNull(foreignHeaders, "the redirect was followed");
        assertFalse(foreignHeaders.containsKey("authorization"));
        assertFalse(foreignHeaders.containsKey("x-amz-date"));
        assertFalse(foreignHeaders.containsKey("x-amz-content-sha256"));
        assertFalse(foreignHeaders.containsKey("x-amz-security-token"));
    }
}
