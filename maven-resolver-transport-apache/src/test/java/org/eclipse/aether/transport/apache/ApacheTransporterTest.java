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
package org.eclipse.aether.transport.apache;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.internal.test.util.http.RecordingTransportListener;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Apache Transporter UT.
 * It does support WebDAV.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ApacheTransporterTest extends HttpTransporterTest {

    public ApacheTransporterTest() {
        super(() -> new ApacheTransporterFactory(standardChecksumExtractor(), new PathProcessorSupport()));
    }

    @Override
    protected Stream<String> supportedCompressionAlgorithms() {
        return Stream.of("gzip", "deflate");
    }

    protected boolean exposeContentCodingInTransportProperties() {
        // see https://issues.apache.org/jira/browse/HTTPCORE-792
        return false;
    }

    @Override
    protected boolean supportsHttp3() {
        return false;
    }

    @Override
    protected boolean supportsHttp2() {
        return false;
    }

    @AfterEach
    @Override
    protected void tearDown() throws Exception {
        // make sure to also release any connection in the global state (otherwise the check for connection leaks will
        // fail)
        GlobalState globalState = GlobalState.get(session);
        if (globalState != null) {
            globalState.close();
        }
        super.tearDown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "https"})
    void testTransfers_SystemProxyAuthenticated(String protocol) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties(protocol, httpServer.getHttpPort())) {
            newTransporter("http://bad.localhost:1/");
            assertTransfers();
        }
    }

    @Test
    void testSystemProxyAuthenticationDisabled() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            session.setConfigProperty(ApacheTransporterConfigurationKeys.CONFIG_PROP_USE_SYSTEM_PROPERTIES, false);
            proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
            newTransporter("http://bad.localhost:1/");
            assertGetStatus(407);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"testpass", "wrong"})
    void testExplicitProxyCredentialsTakePrecedence(String password) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            proxy = new Proxy(
                    Proxy.TYPE_HTTP,
                    httpServer.getHost(),
                    httpServer.getHttpPort(),
                    new AuthenticationBuilder()
                            .addUsername("testuser")
                            .addPassword(password)
                            .build());
            newTransporter("http://bad.localhost:1/");
            if ("testpass".equals(password)) {
                properties.set("http.proxyPassword", "wrong");
                assertTransfers();
            } else {
                assertGetStatus(407);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"Host,other.invalid", "Port,1", "Port,invalid", "Port,", "User,", "Password,wrong"})
    void testSystemProxyCredentialsMustMatch(String property, String value) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
            properties.set("http.proxy" + property, value);
            newTransporter("http://bad.localhost:1/");
            assertGetStatus(407);
        }
    }

    @Test
    void testSystemProxyPasswordDefaultsToEmpty() throws Exception {
        httpServer.setProxyAuthentication("testuser", "");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            properties.set("http.proxyPassword", null);
            newTransporter("http://bad.localhost:1/");
            assertTransfers();
        }
    }

    @ParameterizedTest
    @CsvSource({"http,80", "https,443"})
    void testSystemProxyPortDefaults(String protocol, int port) throws Exception {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set(protocol + ".proxyHost", "proxy.example");
            properties.set(protocol + ".proxyPort", null);
            properties.set(protocol + ".proxyUser", protocol + "-user");
            assertEquals(protocol + "-user", selectSystemProxyUsername(protocol, port));
        }
    }

    @ParameterizedTest
    @CsvSource({"http,http-user", "https,https-user"})
    void testSystemProxyCredentialsPreferTargetProtocol(String protocol, String expectedUsername) throws Exception {
        try (SystemProperties properties = distinctSystemProxyCredentials(8080)) {
            assertEquals(expectedUsername, selectSystemProxyUsername(protocol, 8080));
        }
    }

    @Test
    void testSystemProxyCredentialsFallBackToOppositeProtocol() throws Exception {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set("https.proxyHost", "proxy.example");
            properties.set("https.proxyPort", "8080");
            properties.set("https.proxyUser", "https-user");
            assertEquals("https-user", selectSystemProxyUsername("http", 8080));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 407})
    void testSystemProxyCredentialsNotUsedOnDirectRoute(int status) throws Exception {
        if (status == 401) {
            httpServer.setAuthentication("testuser", "testpass");
        } else {
            httpServer.setProxyAuthentication("testuser", "testpass");
        }
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            properties.set("http.nonProxyHosts", "*");
            newTransporter(httpServer.getHttpUrl());
            assertGetStatus(status);
            assertTrue(httpServer.getLogEntries().stream()
                    .allMatch(entry -> entry.getRequestHeaders().get("Authorization") == null
                            && entry.getRequestHeaders().get("Proxy-Authorization") == null));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSystemProxyCredentialsNotUsedForServer(boolean serverCredentials) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        httpServer.setAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            if (serverCredentials) {
                auth = new AuthenticationBuilder()
                        .addUsername("testuser")
                        .addPassword("testpass")
                        .build();
            }
            newTransporter("http://bad.localhost:1/");
            if (serverCredentials) {
                assertTransfers();
            } else {
                assertGetStatus(401);
                assertTrue(httpServer.getLogEntries().stream()
                        .allMatch(entry -> entry.getRequestHeaders().get("Authorization") == null));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSystemProxyAuthenticationThroughHttpsConnect(boolean authenticateServer) throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        if (authenticateServer) {
            httpServer.setAuthentication("testuser", "testpass");
        }
        Server proxyServer = new Server();
        ServerConnector connector = new ServerConnector(proxyServer);
        connector.setHost(httpServer.getHost());
        proxyServer.addConnector(connector);
        AtomicInteger authenticatedConnects = new AtomicInteger();
        proxyServer.setHandler(new ConnectHandler() {
            @Override
            protected boolean handleAuthentication(Request request, Response response, String address) {
                String expected = "Basic "
                        + Base64.getEncoder().encodeToString("testuser:testpass".getBytes(StandardCharsets.UTF_8));
                if (expected.equals(request.getHeaders().get("Proxy-Authorization"))) {
                    authenticatedConnects.incrementAndGet();
                    return true;
                }
                response.getHeaders().put("Proxy-Authenticate", "Basic realm=\"proxy\"");
                return false;
            }
        });
        proxyServer.start();
        try (SystemProperties properties = systemProxyProperties("https", connector.getLocalPort())) {
            newTransporter(httpServer.getHttpsUrl());
            if (authenticateServer) {
                assertGetStatus(401);
            } else {
                assertTransfers();
            }
            assertTrue(authenticatedConnects.get() > 0);
            assertTrue(httpServer.getLogEntries().stream()
                    .allMatch(entry -> entry.getRequestHeaders().get("Authorization") == null
                            && entry.getRequestHeaders().get("Proxy-Authorization") == null));
        } finally {
            if (transporter != null) {
                transporter.close();
                transporter = null;
            }
            proxyServer.stop();
        }
    }

    @Test
    void testSystemProxyCredentialsNotForwardedAfterRedirectToDirectRoute() throws Exception {
        Server proxyServer = new Server();
        ServerConnector connector = new ServerConnector(proxyServer);
        connector.setHost(httpServer.getHost());
        proxyServer.addConnector(connector);
        AtomicInteger authenticatedRequests = new AtomicInteger();
        proxyServer.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String expected = "Basic "
                        + Base64.getEncoder().encodeToString("testuser:testpass".getBytes(StandardCharsets.UTF_8));
                if (expected.equals(request.getHeaders().get("Proxy-Authorization"))) {
                    authenticatedRequests.incrementAndGet();
                    response.setStatus(302);
                    response.getHeaders().put("Location", httpServer.getHttpUrl() + "/repo/file.txt");
                } else {
                    response.setStatus(407);
                    response.getHeaders().put("Proxy-Authenticate", "Basic realm=\"proxy\"");
                }
                callback.succeeded();
                return true;
            }
        });
        proxyServer.start();
        try (SystemProperties properties = systemProxyProperties("http", connector.getLocalPort())) {
            properties.set("http.nonProxyHosts", httpServer.getHost());
            newTransporter("http://bad.localhost:1/");
            GetTask task = new GetTask(URI.create("repo/file.txt"));
            transporter.get(task);
            assertEquals("test", task.getDataString());
            assertTrue(authenticatedRequests.get() > 0);
            assertTrue(httpServer.getLogEntries().stream()
                    .allMatch(entry -> entry.getRequestHeaders().get("Authorization") == null
                            && entry.getRequestHeaders().get("Proxy-Authorization") == null));
        } finally {
            if (transporter != null) {
                transporter.close();
                transporter = null;
            }
            proxyServer.stop();
        }
    }

    private void assertGetStatus(int status) {
        HttpTransporterException failure = assertThrows(
                HttpTransporterException.class, () -> transporter.get(new GetTask(URI.create("repo/file.txt"))));
        assertEquals(status, failure.getStatusCode());
    }

    private void assertTransfers() throws Exception {
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
        transporter.put(new PutTask(URI.create("repo/upload.txt")).setDataString("upload"));
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "upload.txt")));
    }

    private SystemProperties systemProxyProperties(String protocol, int port) {
        session.setConfigProperty(ApacheTransporterConfigurationKeys.CONFIG_PROP_USE_SYSTEM_PROPERTIES, true);
        SystemProperties properties = new SystemProperties();
        for (String scheme : new String[] {"http", "https"}) {
            properties.set(scheme + ".proxyHost", httpServer.getHost());
            properties.set(scheme + ".proxyPort", Integer.toString(port));
            properties.set(scheme + ".proxyUser", null);
            properties.set(scheme + ".proxyPassword", null);
        }
        properties.set(protocol + ".proxyUser", "testuser");
        properties.set(protocol + ".proxyPassword", "testpass");
        properties.set("http.nonProxyHosts", "");
        return properties;
    }

    private SystemProperties distinctSystemProxyCredentials(int port) {
        SystemProperties properties = new SystemProperties();
        for (String protocol : new String[] {"http", "https"}) {
            properties.set(protocol + ".proxyHost", "proxy.example");
            properties.set(protocol + ".proxyPort", Integer.toString(port));
            properties.set(protocol + ".proxyUser", protocol + "-user");
        }
        return properties;
    }

    private String selectSystemProxyUsername(String targetProtocol, int proxyPort) throws Exception {
        HttpHost proxyHost = new HttpHost("proxy.example", proxyPort);
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute(
                HttpClientContext.HTTP_ROUTE,
                new HttpRoute(new HttpHost("repository.example", -1, targetProtocol), proxyHost));
        context.setCredentialsProvider(new BasicCredentialsProvider());
        context.setRequestConfig(RequestConfig.DEFAULT);
        context.setAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .build());
        Map<String, Header> challenges = Collections.singletonMap(
                AuthSchemes.BASIC.toLowerCase(), new BasicHeader("Proxy-Authenticate", "Basic realm=\"proxy\""));
        Queue<AuthOption> options = new SystemProxyAuthenticationStrategy()
                .select(
                        challenges,
                        proxyHost,
                        new BasicHttpResponse(HttpVersion.HTTP_1_1, 407, "Proxy Authentication Required"),
                        context);
        UsernamePasswordCredentials credentials =
                (UsernamePasswordCredentials) options.remove().getCredentials();
        return credentials.getUserName();
    }

    private static final class SystemProperties implements AutoCloseable {
        private final Map<String, String> previous = new HashMap<>();

        void set(String key, String value) {
            if (!previous.containsKey(key)) {
                previous.put(key, System.getProperty(key));
            }
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        }

        @Override
        public void close() {
            previous.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
    }

    @Test
    void testGet_WebDav() throws Exception {
        httpServer.setWebDav(true);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/dir/file.txt")).setListener(listener);
        ((ApacheTransporter) transporter).getState().setWebDav(true);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), new String(listener.getBaos().toByteArray(), StandardCharsets.UTF_8));
        assertEquals(
                1, httpServer.getLogEntries().size(), httpServer.getLogEntries().toString());
    }

    @Test
    void testPut_WebDav() throws Exception {
        httpServer.setWebDav(true);
        session.setConfigProperty(ConfigurationProperties.HTTP_SUPPORT_WEBDAV, true);
        newTransporter(httpServer.getHttpUrl());

        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/dir1/dir2/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "dir1/dir2/file.txt")));

        assertEquals(
                5, httpServer.getLogEntries().size(), "Expected 5 requests but got: " + httpServer.getLogEntries());
        assertEquals("OPTIONS", httpServer.getLogEntries().get(0).getMethod());
        assertEquals("MKCOL", httpServer.getLogEntries().get(1).getMethod());
        assertEquals("/repo/dir1/dir2/", httpServer.getLogEntries().get(1).getPath());
        assertEquals("MKCOL", httpServer.getLogEntries().get(2).getMethod());
        assertEquals("/repo/dir1/", httpServer.getLogEntries().get(2).getPath());
        assertEquals("MKCOL", httpServer.getLogEntries().get(3).getMethod());
        assertEquals("/repo/dir1/dir2/", httpServer.getLogEntries().get(3).getPath());
        assertEquals("PUT", httpServer.getLogEntries().get(4).getMethod());
    }

    @Test
    void testConnectionReuse() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        session.setCache(new DefaultRepositoryCache());
        for (int i = 0; i < 3; i++) {
            newTransporter(httpServer.getHttpsUrl());
            GetTask task = new GetTask(URI.create("repo/file.txt"));
            transporter.get(task);
            assertEquals("test", task.getDataString());
        }
        PoolStats stats = ((ConnPoolControl<?>)
                        ((ApacheTransporter) transporter).getState().getConnectionManager())
                .getTotalStats();
        assertEquals(1, stats.getAvailable(), stats.toString());
    }

    @Test
    void testConnectionNoReuse() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        session.setCache(new DefaultRepositoryCache());
        session.setConfigProperty(ConfigurationProperties.HTTP_REUSE_CONNECTIONS, false);
        for (int i = 0; i < 3; i++) {
            newTransporter(httpServer.getHttpsUrl());
            GetTask task = new GetTask(URI.create("repo/file.txt"));
            transporter.get(task);
            assertEquals("test", task.getDataString());
        }
        PoolStats stats = ((ConnPoolControl<?>)
                        ((ApacheTransporter) transporter).getState().getConnectionManager())
                .getTotalStats();
        assertEquals(0, stats.getAvailable(), stats.toString());
    }
}
