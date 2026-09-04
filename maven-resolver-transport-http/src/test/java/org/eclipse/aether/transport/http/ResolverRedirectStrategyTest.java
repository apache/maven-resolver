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
package org.eclipse.aether.transport.http;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * UT for {@link ResolverRedirectStrategy}: https-to-http downgrade redirects must not be followed silently, and
 * repository credentials must be bound to the repository's scheme-implied port.
 */
public class ResolverRedirectStrategyTest {
    private static HttpResponse redirectResponse(String location) {
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 302, "Found"));
        response.setHeader("Location", location);
        return response;
    }

    private static HttpContext contextWithTarget(HttpHost target) {
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, target);
        return context;
    }

    @Test
    public void httpsToHttpRedirectRefusedByDefault() {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 443, "https"));

        try {
            strategy.isRedirected(request, response, context);
            fail("Expected ProtocolException");
        } catch (ProtocolException e) {
            assertTrue(e.getMessage().contains(HttpTransporter.FOLLOW_INSECURE_REDIRECTS));
        }
    }

    @Test(expected = ProtocolException.class)
    public void httpsToHttpCrossHostRedirectRefusedByDefault() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://evil.example.org/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 443, "https"));

        strategy.isRedirected(request, response, context);
    }

    @Test
    public void httpsToHttpsRedirectFollowed() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("https://mirror.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 443, "https"));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    public void relativeRedirectFollowed() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("/elsewhere/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 443, "https"));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    public void httpToHttpRedirectFollowed() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://mirror.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 80, "http"));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test
    public void httpsToHttpRedirectFollowedWhenExplicitlyAllowed() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(true);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpContext context = contextWithTarget(new HttpHost("repo.example.com", 443, "https"));

        assertTrue(strategy.isRedirected(request, response, context));
    }

    @Test(expected = ProtocolException.class)
    public void currentSchemeFallsBackToRequestUriWithoutContextTarget() throws ProtocolException {
        ResolverRedirectStrategy strategy = new ResolverRedirectStrategy(false);
        HttpGet request = new HttpGet("https://repo.example.com/g/a/1.0/a-1.0.jar");
        HttpResponse response = redirectResponse("http://repo.example.com/g/a/1.0/a-1.0.jar");

        strategy.isRedirected(request, response, HttpClientContext.create());
    }

    @Test
    public void repositoryCredentialScopeIsBoundToSchemeImpliedPort() {
        assertEquals(443, HttpTransporter.effectivePort(new HttpHost("repo.example.com", -1, "https")));
        assertEquals(80, HttpTransporter.effectivePort(new HttpHost("repo.example.com", -1, "http")));
        assertEquals(8443, HttpTransporter.effectivePort(new HttpHost("repo.example.com", 8443, "https")));
        assertEquals(8081, HttpTransporter.effectivePort(new HttpHost("repo.example.com", 8081, "http")));
    }
}
