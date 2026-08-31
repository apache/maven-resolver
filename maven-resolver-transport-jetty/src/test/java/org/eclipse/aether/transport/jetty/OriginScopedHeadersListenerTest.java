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
package org.eclipse.aether.transport.jetty;

import java.io.File;
import java.net.URI;
import java.util.Arrays;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UT for {@link OriginScopedHeadersListener}: configured headers (and preemptively applied {@code Authorization})
 * must only be sent to the repository origin; any other target - which, on a client bound to one repository, can
 * only be a redirect hop - must not receive them. Nothing is sent over the wire: the listener is invoked directly
 * on requests the way the client-level headers listeners would be.
 */
class OriginScopedHeadersListenerTest {
    private static final URI ORIGIN = URI.create("https://repo.example.com/maven2/");

    private static HttpClient httpClient;

    @BeforeAll
    static void startClient() throws Exception {
        // Ensure java.io.tmpdir exists before starting HttpClient. Jetty's HttpClient loads
        // compression providers (including Brotli) via ServiceLoader during start(). Brotli4j
        // extracts a native library to a temp file; if the tmpdir does not exist yet (Surefire
        // sets it to target/surefire-tmp which is deleted by mvn clean), extraction fails and
        // Brotli is permanently disabled for the JVM lifetime, breaking later compression tests.
        new File(System.getProperty("java.io.tmpdir")).mkdirs();
        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterAll
    static void stopClient() throws Exception {
        httpClient.stop();
    }

    private static OriginScopedHeadersListener listener() {
        return new OriginScopedHeadersListener(ORIGIN, Arrays.asList("Authorization", "Private-Token"));
    }

    private static Request newRequestWithConfiguredHeaders(String uri) {
        Request request = httpClient.newRequest(uri);
        request.headers(h -> {
            h.add("Authorization", "Bearer s3cr3t");
            h.add("Private-Token", "t0ken");
            h.add("Accept", "*/*");
        });
        return request;
    }

    private static void assertConfiguredHeadersPresent(Request request) {
        assertEquals("Bearer s3cr3t", request.getHeaders().get("Authorization"));
        assertEquals("t0ken", request.getHeaders().get("Private-Token"));
        assertNotNull(request.getHeaders().get("Accept"));
    }

    private static void assertConfiguredHeadersStripped(Request request) {
        assertNull(request.getHeaders().get("Authorization"));
        assertNull(request.getHeaders().get("Private-Token"));
        // headers not coming from the scoped set are left alone
        assertNotNull(request.getHeaders().get("Accept"));
    }

    @Test
    void keepsHeadersForOrigin() {
        Request request = newRequestWithConfiguredHeaders("https://repo.example.com/maven2/g/a/1.0/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void keepsHeadersForOriginWithExplicitDefaultPort() {
        Request request = newRequestWithConfiguredHeaders("https://repo.example.com:443/maven2/g/a/1.0/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void keepsHeadersForOriginWithDifferentHostCase() {
        Request request = newRequestWithConfiguredHeaders("https://REPO.Example.COM/maven2/g/a/1.0/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void stripsHeadersForCrossHostRedirectTarget() {
        Request request = newRequestWithConfiguredHeaders("https://cdn.example.net/pool/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void stripsHeadersForSchemeDowngradeOnSameHost() {
        Request request = newRequestWithConfiguredHeaders("http://repo.example.com/maven2/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void stripsHeadersForDifferentPortOnSameHost() {
        Request request = newRequestWithConfiguredHeaders("https://repo.example.com:8443/maven2/a-1.0.jar");
        listener().onHeaders(request);
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void scopedHeaderNameMatchingIsCaseInsensitive() {
        OriginScopedHeadersListener subject = new OriginScopedHeadersListener(ORIGIN, Arrays.asList("private-token"));
        Request request = newRequestWithConfiguredHeaders("https://cdn.example.net/pool/a-1.0.jar");
        subject.onHeaders(request);
        assertNull(request.getHeaders().get("Private-Token"));
        assertNotNull(request.getHeaders().get("Accept"));
    }

    @Test
    void effectivePortFollowsScheme() {
        assertEquals(443, OriginScopedHeadersListener.effectivePort("https", -1));
        assertEquals(80, OriginScopedHeadersListener.effectivePort("http", -1));
        assertEquals(8081, OriginScopedHeadersListener.effectivePort("https", 8081));
    }
}
