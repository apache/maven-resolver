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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link ResolverRedirectStrategy}: https-to-http downgrade redirects must not be followed silently, and
 * repository credentials must be bound to the repository's scheme-implied port.
 */
class ResolverRedirectStrategyTest {
    private static HttpResponse redirectResponse(String location) {
        HttpResponse response = new BasicHttpResponse(302, "Found");
        response.setHeader("Location", location);
        return response;
    }

    private static HttpContext contextWithTarget(HttpHost target) {
        HttpClientContext context = HttpClientContext.create();
        if (target != null) {
            context.setAttribute("http.target_host", target);
            context.setRoute(new HttpRoute(target));
        }
        return context;
    }

    @Test
    void httpsToHttpRedirectRefusedByDefault() {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("https", "repo.example.com", 443));

        ProtocolException e =
                assertThrows(ProtocolException.class, () -> strategy.isRedirected(request, response, context));
        assertTrue(e.getMessage().contains(ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS));
    }

    @Test
    void httpsToHttpCrossHostRedirectRefusedByDefault() {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://evil.example.org/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("https", "repo.example.com", 443));

        assertThrows(ProtocolException.class, () -> strategy.isRedirected(request, response, context));
    }

    @Test
    void httpsToHttpsRedirectFollowed() throws Exception {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("https://mirror.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("https", "repo.example.com", 443));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    void relativeRedirectFollowed() throws Exception {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("/elsewhere/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("https", "repo.example.com", 443));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    void httpToHttpRedirectFollowed() throws Exception {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://mirror.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("http", "repo.example.com", 80));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    void httpsToHttpRedirectFollowedWhenExplicitlyAllowed() throws Exception {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(true);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("https", "repo.example.com", 443));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    void currentSchemeFallsBackToRequestUriWithoutContextTarget() {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");

        assertThrows(
                ProtocolException.class, () -> strategy.isRedirected(request, response, HttpClientContext.create()));
    }

    @Test
    void repositoryCredentialScopeIsBoundToSchemeImpliedPort() {
        assertEquals(443, ApacheTransporter.effectivePort(new HttpHost("https", "repo.example.com", -1)));
        assertEquals(80, ApacheTransporter.effectivePort(new HttpHost("http", "repo.example.com", -1)));
        assertEquals(8443, ApacheTransporter.effectivePort(new HttpHost("https", "repo.example.com", 8443)));
        assertEquals(8081, ApacheTransporter.effectivePort(new HttpHost("http", "repo.example.com", 8081)));
    }
}
