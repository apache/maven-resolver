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

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UT for {@link JdkTransporter.ScopedAuthenticator}: repository credentials must only ever be handed out for
 * challenges originating from the repository base URI itself (and proxy credentials only for the configured
 * proxy), never for arbitrary hosts reached via redirects.
 */
class ScopedAuthenticatorTest {
    private static JdkTransporter.ScopedAuthenticator authenticator(String baseUri, InetSocketAddress proxy) {
        Map<Authenticator.RequestorType, PasswordAuthentication> authentications = new HashMap<>();
        authentications.put(
                Authenticator.RequestorType.SERVER, new PasswordAuthentication("user", "pass".toCharArray()));
        authentications.put(
                Authenticator.RequestorType.PROXY, new PasswordAuthentication("proxyUser", "proxyPass".toCharArray()));
        return new JdkTransporter.ScopedAuthenticator(URI.create(baseUri), proxy, authentications);
    }

    private static PasswordAuthentication challenge(
            Authenticator authenticator, String protocol, String host, int port, Authenticator.RequestorType type) {
        return authenticator.requestPasswordAuthenticationInstance(
                host, null, port, protocol, "prompt", "basic", null, type);
    }

    @Test
    void serverCredentialsReturnedForRepositoryOrigin() {
        Authenticator subject = authenticator("https://repo.example.com/maven2/", null);

        PasswordAuthentication authentication =
                challenge(subject, "https", "repo.example.com", 443, Authenticator.RequestorType.SERVER);
        assertNotNull(authentication);
        assertEquals("user", authentication.getUserName());

        // implicit port (JDK may pass -1 when the URI carries no explicit port)
        assertNotNull(challenge(subject, "https", "repo.example.com", -1, Authenticator.RequestorType.SERVER));
        // host comparison is case-insensitive
        assertNotNull(challenge(subject, "HTTPS", "REPO.EXAMPLE.COM", 443, Authenticator.RequestorType.SERVER));
    }

    @Test
    void serverCredentialsRefusedForForeignHost() {
        Authenticator subject = authenticator("https://repo.example.com/maven2/", null);

        assertNull(challenge(subject, "https", "evil.example.org", 443, Authenticator.RequestorType.SERVER));
    }

    @Test
    void serverCredentialsRefusedForProtocolDowngrade() {
        Authenticator subject = authenticator("https://repo.example.com/maven2/", null);

        assertNull(challenge(subject, "http", "repo.example.com", 80, Authenticator.RequestorType.SERVER));
        assertNull(challenge(subject, "http", "repo.example.com", 443, Authenticator.RequestorType.SERVER));
    }

    @Test
    void serverCredentialsRefusedForOtherPort() {
        Authenticator subject = authenticator("https://repo.example.com/maven2/", null);

        assertNull(challenge(subject, "https", "repo.example.com", 8443, Authenticator.RequestorType.SERVER));
    }

    @Test
    void serverCredentialsHonorExplicitBasePort() {
        Authenticator subject = authenticator("https://repo.example.com:8443/maven2/", null);

        assertNotNull(challenge(subject, "https", "repo.example.com", 8443, Authenticator.RequestorType.SERVER));
        assertNull(challenge(subject, "https", "repo.example.com", 443, Authenticator.RequestorType.SERVER));
        assertNull(challenge(subject, "https", "repo.example.com", -1, Authenticator.RequestorType.SERVER));
    }

    @Test
    void proxyCredentialsOnlyForConfiguredProxy() {
        InetSocketAddress proxy = InetSocketAddress.createUnresolved("proxy.example.com", 3128);
        Authenticator subject = authenticator("https://repo.example.com/maven2/", proxy);

        PasswordAuthentication authentication =
                challenge(subject, "http", "proxy.example.com", 3128, Authenticator.RequestorType.PROXY);
        assertNotNull(authentication);
        assertEquals("proxyUser", authentication.getUserName());

        assertNull(challenge(subject, "http", "other.example.com", 3128, Authenticator.RequestorType.PROXY));
        assertNull(challenge(subject, "http", "proxy.example.com", 8080, Authenticator.RequestorType.PROXY));
    }

    @Test
    void proxyCredentialsRefusedWithoutConfiguredProxy() {
        Authenticator subject = authenticator("https://repo.example.com/maven2/", null);

        assertNull(challenge(subject, "http", "proxy.example.com", 3128, Authenticator.RequestorType.PROXY));
    }
}
