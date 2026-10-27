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
package org.eclipse.aether.util.connector.transport.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransporterUtilsTest {

    @Test
    void getRedirectMode() {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        RemoteRepository repository = new RemoteRepository.Builder("repo", "", "https://host.com").build();

        assertEquals(
                ConfigurationProperties.DEFAULT_REDIRECT_MODE,
                HttpTransporterUtils.getRedirectMode(session, repository));

        session.setConfigProperty(
                ConfigurationProperties.HTTP_REDIRECT_MODE, ConfigurationProperties.RedirectMode.NONE);
        assertEquals(
                ConfigurationProperties.RedirectMode.NONE, HttpTransporterUtils.getRedirectMode(session, repository));

        session.setConfigProperty(
                ConfigurationProperties.HTTP_REDIRECT_MODE + ".repo",
                ConfigurationProperties.RedirectMode.SAME_AUTHORITY);
        assertEquals(
                ConfigurationProperties.RedirectMode.SAME_AUTHORITY,
                HttpTransporterUtils.getRedirectMode(session, repository));

        session = TestUtils.newSession();
        session.setConfigProperty(
                "aether.transport.url.redirectMode.repo", ConfigurationProperties.RedirectMode.SAME_AUTHORITY);
        assertEquals(
                ConfigurationProperties.RedirectMode.SAME_AUTHORITY,
                HttpTransporterUtils.getRedirectMode(session, repository, "aether.transport.url.redirectMode"));

        session.setConfigProperty(ConfigurationProperties.HTTP_REDIRECT_MODE, ConfigurationProperties.RedirectMode.ANY);
        assertEquals(
                ConfigurationProperties.RedirectMode.ANY,
                HttpTransporterUtils.getRedirectMode(session, repository, "aether.transport.url.redirectMode"));
    }

    @Test
    void getRedirectModeSupportsLegacyBooleanProperties() {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        RemoteRepository repository = new RemoteRepository.Builder("repo", "", "https://host.com").build();
        String legacyProperty = "aether.transport.apache.followRedirects";

        assertEquals(
                ConfigurationProperties.RedirectMode.ANY,
                HttpTransporterUtils.getRedirectMode(session, repository, true, legacyProperty));

        session.setConfigProperty(legacyProperty + ".repo", false);
        assertEquals(
                ConfigurationProperties.RedirectMode.NONE,
                HttpTransporterUtils.getRedirectMode(session, repository, true, legacyProperty));

        session.setConfigProperty(
                ConfigurationProperties.HTTP_REDIRECT_MODE, ConfigurationProperties.RedirectMode.SAME_AUTHORITY);
        assertEquals(
                ConfigurationProperties.RedirectMode.SAME_AUTHORITY,
                HttpTransporterUtils.getRedirectMode(session, repository, true, legacyProperty));
    }

    @Test
    void isHttpOriginScopedHeaders() {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        RemoteRepository repository = new RemoteRepository.Builder("repo", "", "https://host.com").build();

        assertTrue(HttpTransporterUtils.isOriginScopedHeaders(session, repository));

        session.setConfigProperty(ConfigurationProperties.HTTP_ORIGIN_SCOPED_HEADERS, false);
        assertFalse(HttpTransporterUtils.isOriginScopedHeaders(session, repository));

        session.setConfigProperty(ConfigurationProperties.HTTP_ORIGIN_SCOPED_HEADERS + ".repo", true);
        assertTrue(HttpTransporterUtils.isOriginScopedHeaders(session, repository));

        session = TestUtils.newSession();
        session.setConfigProperty("aether.transport.apache.originScopedHeaders.repo", false);
        assertFalse(HttpTransporterUtils.isOriginScopedHeaders(
                session, repository, "aether.transport.apache.originScopedHeaders"));

        session.setConfigProperty(ConfigurationProperties.HTTP_ORIGIN_SCOPED_HEADERS, true);
        assertTrue(HttpTransporterUtils.isOriginScopedHeaders(
                session, repository, "aether.transport.apache.originScopedHeaders"));
    }

    @Test
    void isHttpFollowInsecureRedirect() {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        RemoteRepository repository = new RemoteRepository.Builder("repo", "", "https://host.com").build();

        assertFalse(HttpTransporterUtils.isFollowInsecureRedirect(session, repository));

        session.setConfigProperty(ConfigurationProperties.HTTP_FOLLOW_INSECURE_REDIRECT, true);
        assertTrue(HttpTransporterUtils.isFollowInsecureRedirect(session, repository));

        session.setConfigProperty(ConfigurationProperties.HTTP_FOLLOW_INSECURE_REDIRECT + ".repo", false);
        assertFalse(HttpTransporterUtils.isFollowInsecureRedirect(session, repository));

        session = TestUtils.newSession();
        session.setConfigProperty("aether.transport.apache.followInsecureRedirects.repo", true);
        assertTrue(HttpTransporterUtils.isFollowInsecureRedirect(
                session, repository, "aether.transport.apache.followInsecureRedirects"));
    }

    @Test
    void clampRemoteLastModifiedPassesPastAndPresentValues() {
        long testStart = System.currentTimeMillis();
        long past = testStart - 10_000L;
        assertEquals(past, HttpTransporterUtils.clampRemoteLastModified(past));
        assertEquals(0L, HttpTransporterUtils.clampRemoteLastModified(0L));
        assertEquals(-1L, HttpTransporterUtils.clampRemoteLastModified(-1L));
    }

    @Test
    void clampRemoteLastModifiedClampsFutureValuesToLocalClock() {
        long testStart = System.currentTimeMillis();
        long future = testStart + 365L * 24L * 60L * 60L * 1000L;
        long clamped = HttpTransporterUtils.clampRemoteLastModified(future);
        assertTrue(clamped >= testStart, "clamped value must not predate the call");
        assertTrue(clamped <= System.currentTimeMillis(), "clamped value must not exceed the local clock");
        assertTrue(clamped < future, "future-dated value must be clamped");
    }

    @Test
    void goodUris() throws URISyntaxException {
        URI uri;

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("as-is", "", "https://host.com/base/").build());
        assertEquals("https://host.com/base/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("slash-append", "", "https://host.com/base").build());
        assertEquals("https://host.com/base/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("root-append", "", "https://host.com").build());
        assertEquals("https://host.com/", uri.toASCIIString());
    }

    @Test
    void badUris() {
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(new RemoteRepository.Builder("opaque", "", "is:opaque").build()));
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(
                        new RemoteRepository.Builder("query", "", "https://host.com/path?query").build()));
        assertThrows(
                URISyntaxException.class,
                () -> HttpTransporterUtils.getBaseUri(
                        new RemoteRepository.Builder("fragment", "", "https://host.com/path#fragment").build()));
    }

    @Test
    void getBaseUriWithNonAscii() throws URISyntaxException {
        URI uri;

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repø", "default", "https://host.dk/repø").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.dk", uri.getHost());
        assertEquals("/repø/", uri.getPath());
        assertEquals("https://host.dk/rep%C3%B8/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("repø", "default", "https://host.dk/rep%C3%B8").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.dk", uri.getHost());
        assertEquals("/repø/", uri.getPath());
        assertEquals("https://host.dk/rep%C3%B8/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("räpo", "default", "https://host.de/räpo").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.de", uri.getHost());
        assertEquals("/räpo/", uri.getPath());
        assertEquals("https://host.de/r%C3%A4po/", uri.toASCIIString());

        uri = HttpTransporterUtils.getBaseUri(
                new RemoteRepository.Builder("räpo", "default", "https://host.de/r%C3%A4po").build());
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("host.de", uri.getHost());
        assertEquals("/räpo/", uri.getPath());
        assertEquals("https://host.de/r%C3%A4po/", uri.toASCIIString());
    }

    @Test
    void getHttpServiceUnavailableCodesUsesDefaultCodes() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        RemoteRepository repository = new RemoteRepository.Builder("test", "default", "https://example.com/").build();

        Set<Integer> codes = HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository);

        assertEquals(new HashSet<>(Arrays.asList(429, 502, 503, 504)), codes);
    }

    @Test
    void getHttpServiceUnavailableCodesSupportsCustomConfiguration() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        RemoteRepository repository = new RemoteRepository.Builder("test", "default", "https://example.com/").build();

        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE, "429,502");

        Set<Integer> codes = HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository);

        assertEquals(new HashSet<>(Arrays.asList(429, 502)), codes);
    }

    @Test
    void getHttpServiceUnavailableCodesSupportsRepositorySpecificConfiguration() {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        RemoteRepository repository = new RemoteRepository.Builder("test", "default", "https://example.com/").build();

        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE + ".test", "502,504");

        Set<Integer> codes = HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository);

        assertEquals(new HashSet<>(Arrays.asList(502, 504)), codes);
    }
}
