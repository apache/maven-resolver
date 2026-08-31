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

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for the origin-scoped manual redirect following of {@link JdkTransporter}: configured headers (and
 * preemptively applied {@code Authorization}) must only ever ride along to the repository origin; a redirect hop
 * that leaves the origin (cross-host, scheme downgrade or different port) must not carry them. No network is
 * involved: the hop-request construction and follow decisions are exercised directly.
 */
class JdkTransporterRedirectTest {
    private static final URI BASE_URI = URI.create("https://repo.example.com/maven2/");

    private static Set<String> excludedHeaders() {
        TreeSet<String> excluded = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        excluded.add("Authorization");
        excluded.add("Private-Token");
        return excluded;
    }

    private static HttpRequest requestWithConfiguredHeaders() {
        return HttpRequest.newBuilder()
                .uri(BASE_URI.resolve("g/a/1.0/a-1.0.jar"))
                .header("Authorization", "Bearer s3cr3t")
                .header("Private-Token", "t0ken")
                .header("Cache-Control", "no-cache, no-store")
                .GET()
                .build();
    }

    @Test
    void crossHostHopDropsScopedHeaders() {
        HttpRequest hop = JdkTransporter.redirectRequest(
                requestWithConfiguredHeaders(),
                302,
                URI.create("https://cdn.example.net/pool/a-1.0.jar"),
                BASE_URI,
                excludedHeaders());
        assertFalse(hop.headers().firstValue("Authorization").isPresent());
        assertFalse(hop.headers().firstValue("Private-Token").isPresent());
        // headers not coming from the scoped set are left alone
        assertTrue(hop.headers().firstValue("Cache-Control").isPresent());
        assertEquals("GET", hop.method());
    }

    @Test
    void schemeDowngradeOrOtherPortHopDropsScopedHeaders() {
        HttpRequest hop = JdkTransporter.redirectRequest(
                requestWithConfiguredHeaders(),
                302,
                URI.create("http://repo.example.com/maven2/a-1.0.jar"),
                BASE_URI,
                excludedHeaders());
        assertFalse(hop.headers().firstValue("Authorization").isPresent());

        hop = JdkTransporter.redirectRequest(
                requestWithConfiguredHeaders(),
                302,
                URI.create("https://repo.example.com:8443/maven2/a-1.0.jar"),
                BASE_URI,
                excludedHeaders());
        assertFalse(hop.headers().firstValue("Authorization").isPresent());
    }

    @Test
    void sameOriginHopKeepsAllHeaders() {
        HttpRequest hop = JdkTransporter.redirectRequest(
                requestWithConfiguredHeaders(),
                302,
                BASE_URI.resolve("g/a/1.0/a-1.0.jar.moved"),
                BASE_URI,
                excludedHeaders());
        assertEquals("Bearer s3cr3t", hop.headers().firstValue("Authorization").orElse(null));
        assertEquals("t0ken", hop.headers().firstValue("Private-Token").orElse(null));
        assertTrue(hop.headers().firstValue("Cache-Control").isPresent());
    }

    @Test
    void sameOriginComparisonIsCaseInsensitiveAndPortAware() {
        HttpRequest hop = JdkTransporter.redirectRequest(
                requestWithConfiguredHeaders(),
                302,
                URI.create("HTTPS://REPO.EXAMPLE.COM:443/maven2/a-1.0.jar"),
                BASE_URI,
                excludedHeaders());
        assertTrue(hop.headers().firstValue("Authorization").isPresent());
    }

    @Test
    void scopedHeaderNameMatchingIsCaseInsensitive() {
        HttpRequest previous = HttpRequest.newBuilder()
                .uri(BASE_URI.resolve("a.jar"))
                .header("AUTHORIZATION", "Bearer s3cr3t")
                .GET()
                .build();
        HttpRequest hop = JdkTransporter.redirectRequest(
                previous, 302, URI.create("https://cdn.example.net/a.jar"), BASE_URI, excludedHeaders());
        assertFalse(hop.headers().firstValue("Authorization").isPresent());
    }

    @Test
    void seeOtherBecomesGetWhileTemporaryRedirectKeepsMethod() {
        HttpRequest put = HttpRequest.newBuilder()
                .uri(BASE_URI.resolve("a.jar"))
                .PUT(HttpRequest.BodyPublishers.ofString("payload"))
                .build();
        assertEquals(
                "GET",
                JdkTransporter.redirectRequest(put, 303, BASE_URI.resolve("b.jar"), BASE_URI, excludedHeaders())
                        .method());
        assertEquals(
                "PUT",
                JdkTransporter.redirectRequest(put, 307, BASE_URI.resolve("b.jar"), BASE_URI, excludedHeaders())
                        .method());
    }

    @Test
    void followableRedirectResolvesAbsoluteAndRelativeLocations() {
        URI requestUri = BASE_URI.resolve("g/a/1.0/a-1.0.jar");
        assertEquals(
                URI.create("https://cdn.example.net/pool/a-1.0.jar"),
                JdkTransporter.followableRedirect(302, requestUri, "https://cdn.example.net/pool/a-1.0.jar"));
        assertEquals(
                URI.create("https://repo.example.com/maven2/g/a/1.0/other.jar"),
                JdkTransporter.followableRedirect(301, requestUri, "other.jar"));
    }

    @Test
    void nonRedirectsAreNotFollowed() {
        URI requestUri = BASE_URI.resolve("a.jar");
        // not a redirect status
        assertNull(JdkTransporter.followableRedirect(200, requestUri, "https://cdn.example.net/a.jar"));
        assertNull(JdkTransporter.followableRedirect(404, requestUri, "https://cdn.example.net/a.jar"));
        // redirect status without Location
        assertNull(JdkTransporter.followableRedirect(302, requestUri, null));
        // parity with HttpClient.Redirect.NORMAL: no https -> http downgrade
        assertNull(JdkTransporter.followableRedirect(302, requestUri, "http://repo.example.com/maven2/a.jar"));
        // only http(s) targets
        assertNull(JdkTransporter.followableRedirect(302, requestUri, "ftp://repo.example.com/a.jar"));
        assertNotNull(JdkTransporter.followableRedirect(308, requestUri, "https://cdn.example.net/a.jar"));
    }

    @Test
    void effectivePortFollowsScheme() {
        assertEquals(443, JdkTransporter.effectivePort("https", -1));
        assertEquals(80, JdkTransporter.effectivePort("http", -1));
        assertEquals(8081, JdkTransporter.effectivePort("https", 8081));
        assertTrue(JdkTransporter.isSameOrigin(
                URI.create("https://repo.example.com/maven2/"), URI.create("https://repo.example.com:443/other")));
        assertFalse(JdkTransporter.isSameOrigin(
                URI.create("https://repo.example.com/maven2/"), URI.create("https://repo.example.com:8443/other")));
    }
}
