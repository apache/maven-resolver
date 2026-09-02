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
package org.eclipse.aether.transport.apache5;

import java.util.Arrays;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link OriginScopedHeadersInterceptor}: configured headers must only be sent to the repository origin;
 * any other target (cross-host, downgraded scheme or different port - e.g. after a redirect) must not receive them.
 */
public class OriginScopedHeadersInterceptorTest {
    private static final HttpHost ORIGIN = new HttpHost("https", "repo.example.com", -1);

    private final OriginScopedHeadersInterceptor interceptor =
            new OriginScopedHeadersInterceptor(ORIGIN, Arrays.asList("Authorization", "Private-Token"));

    private static HttpRequest newRequestWithConfiguredHeaders() {
        HttpRequest request = new BasicHttpRequest("GET", "/base/g/a/1.0/a-1.0.jar");
        request.setHeader("Authorization", "Bearer s3cr3t");
        request.setHeader("Private-Token", "t0ken");
        request.setHeader("Accept", "*/*");
        return request;
    }

    private static HttpContext contextTargeting(HttpHost target) {
        org.apache.hc.client5.http.protocol.HttpClientContext context =
                org.apache.hc.client5.http.protocol.HttpClientContext.create();
        if (target != null) {
            int port = target.getPort() >= 0
                    ? target.getPort()
                    : ("https".equalsIgnoreCase(target.getSchemeName()) ? 443 : 80);
            context.setRoute(new org.apache.hc.client5.http.HttpRoute(
                    new HttpHost(target.getSchemeName(), target.getHostName(), port)));
            context.setAttribute("http.target_host", target);
        }
        return context;
    }

    private static void assertConfiguredHeadersPresent(HttpRequest request) {
        assertTrue(request.containsHeader("Authorization"));
        assertTrue(request.containsHeader("Private-Token"));
        assertTrue(request.containsHeader("Accept"));
    }

    private static void assertConfiguredHeadersStripped(HttpRequest request) {
        assertFalse(request.containsHeader("Authorization"));
        assertFalse(request.containsHeader("Private-Token"));
        // headers not coming from the configured map are left alone
        assertTrue(request.containsHeader("Accept"));
    }

    @Test
    void keepsHeadersForOrigin() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("https", "repo.example.com", -1)));
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void keepsHeadersForOriginWithExplicitDefaultPort() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("https", "repo.example.com", 443)));
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void keepsHeadersForOriginWithDifferentHostCase() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("HTTPS", "REPO.Example.COM", -1)));
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void stripsHeadersForCrossHostTarget() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("https", "cdn.example.net", -1)));
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void stripsHeadersForSchemeDowngradeOnSameHost() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("http", "repo.example.com", -1)));
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void stripsHeadersForDifferentPortOnSameHost() throws Exception {
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(new HttpHost("https", "repo.example.com", 8443)));
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void stripsHeadersWhenTargetHostUnknown() throws Exception {
        // fail closed: no determinable target means configured headers are not attached
        HttpRequest request = newRequestWithConfiguredHeaders();
        interceptor.process(request, null, contextTargeting(null));
        assertConfiguredHeadersStripped(request);
    }

    @Test
    void noConfiguredHeadersIsNoOp() throws Exception {
        OriginScopedHeadersInterceptor empty =
                new OriginScopedHeadersInterceptor(ORIGIN, Arrays.asList(new Object[] {null}));
        HttpRequest request = newRequestWithConfiguredHeaders();
        empty.process(request, null, contextTargeting(new HttpHost("https", "cdn.example.net", -1)));
        assertConfiguredHeadersPresent(request);
    }

    @Test
    void effectivePortFollowsScheme() {
        assertEquals(443, OriginScopedHeadersInterceptor.schemeDefaultPort(new HttpHost("https", "h", -1)));
        assertEquals(80, OriginScopedHeadersInterceptor.schemeDefaultPort(new HttpHost("http", "h", -1)));
        assertEquals(8081, OriginScopedHeadersInterceptor.schemeDefaultPort(new HttpHost("https", "h", 8081)));
    }
}
