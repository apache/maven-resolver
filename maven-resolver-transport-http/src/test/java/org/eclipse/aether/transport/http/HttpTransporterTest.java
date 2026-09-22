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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.NoHttpResponseException;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transport.http.RFC9457.HttpRFC9457Exception;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public class HttpTransporterTest {

    static {
        System.setProperty(
                "javax.net.ssl.trustStore", new File("src/test/resources/ssl/server-store").getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "server-pwd");
        System.setProperty("javax.net.ssl.keyStore", new File("src/test/resources/ssl/client-store").getAbsolutePath());
        System.setProperty("javax.net.ssl.keyStorePassword", "client-pwd");
    }

    @Rule
    public TestName testName = new TestName();

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private File repoDir;

    private HttpServer httpServer;

    private Authentication auth;

    private Proxy proxy;

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("test", "default", url)
                .setAuthentication(auth)
                .setProxy(proxy)
                .build();
    }

    private void newTransporter(String url) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        transporter = factory.newInstance(session, newRepo(url));
    }

    private static final long OLD_FILE_TIMESTAMP = 160660800000L;

    @Before
    public void setUp() throws Exception {
        System.out.println("=== " + testName.getMethodName() + " ===");
        session = TestUtils.newSession();
        factory = new HttpTransporterFactory();
        repoDir = TestFileUtils.createTempDir();
        TestFileUtils.writeString(new File(repoDir, "file.txt"), "test");
        TestFileUtils.writeString(new File(repoDir, "dir/file.txt"), "test");
        TestFileUtils.writeString(new File(repoDir, "dir/oldFile.txt"), "oldTest", OLD_FILE_TIMESTAMP);
        TestFileUtils.writeString(new File(repoDir, "empty.txt"), "");
        TestFileUtils.writeString(new File(repoDir, "some space.txt"), "space");
        File resumable = new File(repoDir, "resume.txt");
        TestFileUtils.writeString(resumable, "resumable");
        resumable.setLastModified(System.currentTimeMillis() - 90 * 1000);
        httpServer = new HttpServer().setRepoDir(repoDir).start();
        newTransporter(httpServer.getHttpUrl());
    }

    @After
    public void tearDown() throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        factory = null;
        session = null;
    }

    @Test
    public void testTransfersSystemProxyAuthenticated() throws Exception {
        testTransfersSystemProxyAuthenticated("http");
        testTransfersSystemProxyAuthenticated("https");
    }

    @Test
    public void testExplicitProxyCredentialsTakePrecedence() throws Exception {
        testExplicitProxyCredentialsTakePrecedence("testpass");
        testExplicitProxyCredentialsTakePrecedence("wrong");
    }

    @Test
    public void testSystemProxyCredentialsMustMatch() throws Exception {
        testSystemProxyCredentialsMustMatch("Host", "other.invalid");
        testSystemProxyCredentialsMustMatch("Port", "1");
        testSystemProxyCredentialsMustMatch("Port", "invalid");
        testSystemProxyCredentialsMustMatch("Port", null);
        testSystemProxyCredentialsMustMatch("User", null);
        testSystemProxyCredentialsMustMatch("Password", "wrong");
    }

    @Test
    public void testSystemProxyCredentialsNotUsedOnDirectRoute() throws Exception {
        testSystemProxyCredentialsNotUsedOnDirectRoute(401);
        testSystemProxyCredentialsNotUsedOnDirectRoute(407);
    }

    @Test
    public void testSystemProxyCredentialsNotUsedForServer() throws Exception {
        testSystemProxyCredentialsNotUsedForServer(false);
        testSystemProxyCredentialsNotUsedForServer(true);
    }

    @Test
    public void testSystemProxyAuthenticationThroughHttpsConnect() throws Exception {
        testSystemProxyAuthenticationThroughHttpsConnect(false);
        testSystemProxyAuthenticationThroughHttpsConnect(true);
    }

    private void testTransfersSystemProxyAuthenticated(String protocol) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties(protocol, httpServer.getHttpPort())) {
            newTransporter("http://bad.localhost:1/");
            assertTransfers();
        }
    }

    @Test
    public void testSystemProxyAuthenticationDisabled() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            session.setConfigProperty(HttpTransporter.USE_SYSTEM_PROPERTIES, false);
            proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
            newTransporter("http://bad.localhost:1/");
            assertGetStatus(407);
        }
    }

    private void testExplicitProxyCredentialsTakePrecedence(String password) throws Exception {
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

    private void testSystemProxyCredentialsMustMatch(String property, String value) throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
            properties.set("http.proxy" + property, value);
            newTransporter("http://bad.localhost:1/");
            assertGetStatus(407);
        }
    }

    @Test
    public void testSystemProxyPasswordDefaultsToEmpty() throws Exception {
        httpServer.setProxyAuthentication("testuser", "");
        try (SystemProperties properties = systemProxyProperties("http", httpServer.getHttpPort())) {
            properties.set("http.proxyPassword", null);
            newTransporter("http://bad.localhost:1/");
            assertTransfers();
        }
    }

    @Test
    public void testSystemProxyPortDefaults() throws Exception {
        for (String protocol : new String[] {"http", "https"}) {
            int defaultPort = "https".equals(protocol) ? 443 : 80;
            try (SystemProperties properties = new SystemProperties()) {
                properties.set(protocol + ".proxyHost", "proxy.example");
                properties.set(protocol + ".proxyPort", null);
                properties.set(protocol + ".proxyUser", protocol + "-user");
                assertEquals(protocol + "-user", selectSystemProxyUsername(protocol, defaultPort));
            }
        }
    }

    @Test
    public void testSystemProxyCredentialsPreferTargetProtocol() throws Exception {
        try (SystemProperties properties = distinctSystemProxyCredentials(8080)) {
            assertEquals("http-user", selectSystemProxyUsername("http", 8080));
            assertEquals("https-user", selectSystemProxyUsername("https", 8080));
        }
    }

    @Test
    public void testSystemProxyCredentialsFallBackToOppositeProtocol() throws Exception {
        try (SystemProperties properties = new SystemProperties()) {
            properties.set("https.proxyHost", "proxy.example");
            properties.set("https.proxyPort", "8080");
            properties.set("https.proxyUser", "https-user");
            assertEquals("https-user", selectSystemProxyUsername("http", 8080));
        }
    }

    @Test
    public void testSystemProxyCredentialsResetWhenTargetProtocolChanges() throws Exception {
        try (SystemProperties properties = distinctSystemProxyCredentials(8080)) {
            HttpHost proxyHost = new HttpHost("proxy.example", 8080);
            HttpClientContext context = newSystemProxyContext("http", proxyHost);
            SystemProxyAuthenticationStrategy strategy = new SystemProxyAuthenticationStrategy();
            AuthOption option = selectSystemProxyCredentials(strategy, proxyHost, context);
            AuthState state = new AuthState();
            state.update(option.getAuthScheme(), option.getCredentials());
            context.setAttribute(HttpClientContext.PROXY_AUTH_STATE, state);

            context.setAttribute(
                    HttpClientContext.HTTP_ROUTE,
                    new HttpRoute(new HttpHost("repository.example", -1, "https"), proxyHost));
            BasicHttpRequest request = new BasicHttpRequest("CONNECT", "repository.example:443");
            request.addHeader("Proxy-Authorization", "Basic stale");
            strategy.process(request, context);

            assertNull(state.getCredentials());
            assertNull(request.getFirstHeader("Proxy-Authorization"));
            AuthOption redirectedOption = selectSystemProxyCredentials(strategy, proxyHost, context);
            UsernamePasswordCredentials redirectedCredentials =
                    (UsernamePasswordCredentials) redirectedOption.getCredentials();
            assertEquals("https-user", redirectedCredentials.getUserName());
        }
    }

    private void testSystemProxyCredentialsNotUsedOnDirectRoute(int status) throws Exception {
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
                    .allMatch(entry -> entry.headers.get("Authorization") == null
                            && entry.headers.get("Proxy-Authorization") == null));
        }
    }

    private void testSystemProxyCredentialsNotUsedForServer(boolean serverCredentials) throws Exception {
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
                        .allMatch(entry -> entry.headers.get("Authorization") == null));
            }
        }
    }

    private void testSystemProxyAuthenticationThroughHttpsConnect(boolean authenticateServer) throws Exception {
        httpServer.addSslConnector();
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
            protected boolean handleAuthentication(
                    HttpServletRequest request, HttpServletResponse response, String address) {
                String expected = "Basic "
                        + Base64.getEncoder().encodeToString("testuser:testpass".getBytes(StandardCharsets.UTF_8));
                if (expected.equals(request.getHeader("Proxy-Authorization"))) {
                    authenticatedConnects.incrementAndGet();
                    return true;
                }
                response.setHeader("Proxy-Authenticate", "Basic realm=\"proxy\"");
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
                    .allMatch(entry -> entry.headers.get("Authorization") == null
                            && entry.headers.get("Proxy-Authorization") == null));
        } finally {
            if (transporter != null) {
                transporter.close();
                transporter = null;
            }
            proxyServer.stop();
        }
    }

    @Test
    public void testSystemProxyCredentialsNotForwardedAfterRedirectToDirectRoute() throws Exception {
        Server proxyServer = new Server();
        ServerConnector connector = new ServerConnector(proxyServer);
        connector.setHost(httpServer.getHost());
        proxyServer.addConnector(connector);
        AtomicInteger authenticatedRequests = new AtomicInteger();
        proxyServer.setHandler(new AbstractHandler() {
            @Override
            public void handle(
                    String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                String expected = "Basic "
                        + Base64.getEncoder().encodeToString("testuser:testpass".getBytes(StandardCharsets.UTF_8));
                if (expected.equals(request.getHeader("Proxy-Authorization"))) {
                    authenticatedRequests.incrementAndGet();
                    response.setStatus(302);
                    response.setHeader("Location", httpServer.getHttpUrl() + "/repo/file.txt");
                } else {
                    response.setStatus(407);
                    response.setHeader("Proxy-Authenticate", "Basic realm=\"proxy\"");
                }
                baseRequest.setHandled(true);
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
                    .allMatch(entry -> entry.headers.get("Authorization") == null
                            && entry.headers.get("Proxy-Authorization") == null));
        } finally {
            if (transporter != null) {
                transporter.close();
                transporter = null;
            }
            proxyServer.stop();
        }
    }

    private void assertGetStatus(int status) {
        HttpResponseException failure = assertThrows(
                HttpResponseException.class, () -> transporter.get(new GetTask(URI.create("repo/file.txt"))));
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
        session.setConfigProperty(HttpTransporter.USE_SYSTEM_PROPERTIES, true);
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
        HttpClientContext context = newSystemProxyContext(targetProtocol, proxyHost);
        AuthOption option = selectSystemProxyCredentials(new SystemProxyAuthenticationStrategy(), proxyHost, context);
        return ((UsernamePasswordCredentials) option.getCredentials()).getUserName();
    }

    private HttpClientContext newSystemProxyContext(String targetProtocol, HttpHost proxyHost) {
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute(
                HttpClientContext.HTTP_ROUTE,
                new HttpRoute(new HttpHost("repository.example", -1, targetProtocol), proxyHost));
        context.setCredentialsProvider(new BasicCredentialsProvider());
        context.setRequestConfig(RequestConfig.DEFAULT);
        context.setAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .build());
        return context;
    }

    private AuthOption selectSystemProxyCredentials(
            SystemProxyAuthenticationStrategy strategy, HttpHost proxyHost, HttpClientContext context)
            throws Exception {
        Map<String, Header> challenges = Collections.singletonMap(
                AuthSchemes.BASIC.toLowerCase(), new BasicHeader("Proxy-Authenticate", "Basic realm=\"proxy\""));
        Queue<AuthOption> options = strategy.select(
                challenges,
                proxyHost,
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 407, "Proxy Authentication Required"),
                context);
        return options.remove();
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
    public void testClassify() {
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new HttpResponseException(403, "Forbidden")));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new HttpResponseException(404, "Not Found")));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new HttpResponseException(410, "Gone")));
    }

    @Test
    public void testPeek() throws Exception {
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testRetryHandlerdefaultCountpositive() throws Exception {
        httpServer.setConnectionsToClose(3);
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testRetryHandlerdefaultCountnegative() throws Exception {
        httpServer.setConnectionsToClose(4);
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (NoHttpResponseException expected) {
        }
    }

    @Test
    public void testRetryHandlerexplicitCountpositive() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 10);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setConnectionsToClose(10);
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testRetryHandlerdisabled() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 0);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setConnectionsToClose(1);
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
        } catch (NoHttpResponseException expected) {
        }
    }

    @Test
    public void testPeekNotFound() throws Exception {
        try {
            transporter.peek(new PeekTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testPeekClosed() throws Exception {
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPeekAuthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testPeekUnauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPeekProxyAuthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth);
        newTransporter("http://bad.localhost:1/");
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testPeekProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPeekSSL() throws Exception {
        httpServer.addSslConnector();
        newTransporter(httpServer.getHttpsUrl());
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    public void testPeekRedirect() throws Exception {
        httpServer.addSslConnector();
        transporter.peek(new PeekTask(URI.create("redirect/file.txt")));
        transporter.peek(new PeekTask(URI.create("redirect/file.txt?scheme=https")));
    }

    @Test
    public void testGetToMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetToFile() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task =
                new GetTask(URI.create("repo/file.txt")).setDataFile(file).setListener(listener);
        transporter.get(task);
        assertEquals("test", TestFileUtils.readString(file));
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("test", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetToFileTimestamp() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/dir/oldFile.txt"))
                .setDataFile(file)
                .setListener(listener);
        transporter.get(task);
        assertEquals("oldTest", TestFileUtils.readString(file));
        assertEquals(0L, listener.dataOffset);
        assertEquals(7L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("oldTest", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(file.lastModified(), OLD_FILE_TIMESTAMP);
    }

    @Test
    public void testGetEmptyResource() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task =
                new GetTask(URI.create("repo/empty.txt")).setDataFile(file).setListener(listener);
        transporter.get(task);
        assertEquals("", TestFileUtils.readString(file));
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetEncodedResourcePath() throws Exception {
        GetTask task = new GetTask(URI.create("repo/some%20space.txt"));
        transporter.get(task);
        assertEquals("space", task.getDataString());
    }

    @Test
    public void testGetAuthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetUnauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGetProxyAuthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        Authentication auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth);
        newTransporter("http://bad.localhost:1/");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGetSSL() throws Exception {
        httpServer.addSslConnector();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetHTTPSUnknownSecurityMode() throws Exception {
        session.setConfigProperty("aether.connector.https.securityMode", "unknown");
        httpServer.addSelfSignedSslConnector();
        try {
            newTransporter(httpServer.getHttpsUrl());
            fail("Unsupported security mode");
        } catch (IllegalArgumentException a) {
            // good
        }
    }

    @Test
    public void testGetHTTPSInsecureSecurityMode() throws Exception {
        // here we use alternate server-store-selfigned key (as the key set it static initalizer is probably already
        // used to init SSLContext/SSLSocketFactory/etc
        session.setConfigProperty(
                "aether.connector.https.securityMode", ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
        httpServer.addSelfSignedSslConnector();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetWebDav() throws Exception {
        httpServer.setWebDav(true);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/dir/file.txt")).setListener(listener);
        ((HttpTransporter) transporter).getState().setWebDav(true);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(
                httpServer.getLogEntries().toString(),
                1,
                httpServer.getLogEntries().size());
    }

    @Test
    public void testGetRedirect() throws Exception {
        httpServer.addSslConnector();
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("redirect/file.txt?scheme=https")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetResume() throws Exception {
        File file = TestFileUtils.createTempFile("re");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataFile(file, true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.startedCount);
        assertEquals(2L, listener.dataOffset);
        assertEquals(9, listener.dataLength);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("sumable", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetResumeLocalContentsOutdated() throws Exception {
        File file = TestFileUtils.createTempFile("re");
        file.setLastModified(System.currentTimeMillis() - 5 * 60 * 1000);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataFile(file, true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.startedCount);
        assertEquals(0L, listener.dataOffset);
        assertEquals(9, listener.dataLength);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("resumable", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetResumeRangesNotSupportedByServer() throws Exception {
        httpServer.setRangeSupport(false);
        File file = TestFileUtils.createTempFile("re");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataFile(file, true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.startedCount);
        assertEquals(0L, listener.dataOffset);
        assertEquals(9, listener.dataLength);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("resumable", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetChecksumsNexus() throws Exception {
        httpServer.setChecksumHeader(HttpServer.ChecksumHeader.NEXUS);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(
                "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", task.getChecksums().get("SHA-1"));
    }

    @Test
    public void testGetChecksumsXChecksum() throws Exception {
        httpServer.setChecksumHeader(HttpServer.ChecksumHeader.XCHECKSUM);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(
                "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", task.getChecksums().get("SHA-1"));
    }

    @Test
    public void testGetFileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File file = TestFileUtils.createTempFile("failure");
            transporter.get(new GetTask(URI.create("repo/file.txt")).setDataFile(file));
            assertTrue(i + ", " + file.getAbsolutePath(), file.delete());
        }
    }

    @Test
    public void testGetNotFound() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testGetClosed() throws Exception {
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGetStartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @Test
    public void testGetProgressCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(1, listener.progressedCount);
    }

    @Test
    public void testPutFromMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutFromFile() throws Exception {
        File file = TestFileUtils.createTempFile("upload");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataFile(file);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutEmptyResource() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutEncodedResourcePath() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/some%20space.txt"))
                .setListener(listener)
                .setDataString("OK");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(2L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("OK", TestFileUtils.readString(new File(repoDir, "some space.txt")));
    }

    @Test
    public void testPutAuthenticatedExpectContinue() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutAuthenticatedExpectContinueBroken() throws Exception {
        // this makes OPTIONS recover, and have only 1 PUT (startedCount=1 as OPTIONS is not counted)
        session.setConfigProperty(HttpTransporter.SUPPORT_WEBDAV, true);
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setExpectSupport(HttpServer.ExpectContinue.BROKEN);
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutAuthenticatedExpectContinueRejected() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setExpectSupport(HttpServer.ExpectContinue.FAIL);
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutAuthenticatedExpectContinueDisabled() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_EXPECT_CONTINUE, false);
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setExpectSupport(HttpServer.ExpectContinue.FAIL); // if transport tries Expect/Continue explode
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount); // w/ expectContinue enabled would have here 2
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutAuthenticatedExpectContinueRejectedExplicitlyConfiguredHeader() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Expect", "100-continue");
        session.setConfigProperty(ConfigurationProperties.HTTP_HEADERS + ".test", headers);
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setExpectSupport(HttpServer.ExpectContinue.FAIL);
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutUnauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @Test
    public void testPutProxyAuthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        Authentication auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth);
        newTransporter("http://bad.localhost:1/");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (HttpResponseException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @Test
    public void testPutSSL() throws Exception {
        httpServer.addSslConnector();
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutWebDav() throws Exception {
        httpServer.setWebDav(true);
        session.setConfigProperty(HttpTransporter.SUPPORT_WEBDAV, true);
        newTransporter(httpServer.getHttpUrl());

        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/dir1/dir2/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "dir1/dir2/file.txt")));

        assertEquals(5, httpServer.getLogEntries().size());
        assertEquals("OPTIONS", httpServer.getLogEntries().get(0).method);
        assertEquals("MKCOL", httpServer.getLogEntries().get(1).method);
        assertEquals("/repo/dir1/dir2/", httpServer.getLogEntries().get(1).path);
        assertEquals("MKCOL", httpServer.getLogEntries().get(2).method);
        assertEquals("/repo/dir1/", httpServer.getLogEntries().get(2).path);
        assertEquals("MKCOL", httpServer.getLogEntries().get(3).method);
        assertEquals("/repo/dir1/dir2/", httpServer.getLogEntries().get(3).path);
        assertEquals("PUT", httpServer.getLogEntries().get(4).method);
    }

    @Test
    public void testPutFileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File src = TestFileUtils.createTempFile("upload");
            File dst = new File(repoDir, "file.txt");
            transporter.put(new PutTask(URI.create("repo/file.txt")).setDataFile(src));
            assertTrue(i + ", " + src.getAbsolutePath(), src.delete());
            assertTrue(i + ", " + dst.getAbsolutePath(), dst.delete());
        }
    }

    @Test
    public void testPutClosed() throws Exception {
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPutStartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @Test
    public void testPutProgressCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(1, listener.progressedCount);
    }

    @Test
    public void testGetPutAuthCache() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        GetTask get = new GetTask(URI.create("repo/file.txt"));
        transporter.get(get);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(1, listener.startedCount);
    }

    @Test
    public void testPutPreemptiveIsDefault() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
    }

    @Test
    public void testPutAuthCache() throws Exception {
        session.setConfigProperty(HttpTransporter.PREEMPTIVE_PUT_AUTH, false);
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(2, httpServer.getLogEntries().size()); // put (challenged) + put w/ auth
        httpServer.getLogEntries().clear();
        task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
    }

    @Test
    public void testPutAuthCachePreemptive() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        session.setConfigProperty(ConfigurationProperties.HTTP_PREEMPTIVE_AUTH, true);
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
        httpServer.getLogEntries().clear();
        task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
    }

    @Test(timeout = 20000L)
    public void testConcurrency() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            final String path = "repo/file.txt?i=" + i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        for (int j = 0; j < 100; j++) {
                            GetTask task = new GetTask(URI.create(path));
                            transporter.get(task);
                            assertEquals("test", task.getDataString());
                        }
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                        System.err.println(path);
                        t.printStackTrace();
                    }
                }
            };
            threads[i].setName("Task-" + i);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertNull(String.valueOf(error.get()), error.get());
    }

    @Test(timeout = 1000L)
    public void testConnectTimeout() throws Exception {
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, 100);
        int port = 1;
        newTransporter("http://localhost:" + port);
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (ConnectTimeoutException | ConnectException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test(timeout = 1000L)
    public void testRequestTimeout() throws Exception {
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, 100);
        ServerSocket server = new ServerSocket(0);
        newTransporter("http://localhost:" + server.getLocalPort());
        try {
            try {
                transporter.get(new GetTask(URI.create("repo/file.txt")));
                fail("Expected error");
            } catch (SocketTimeoutException e) {
                assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
            }
        } finally {
            server.close();
        }
    }

    @Test
    public void testUserAgent() throws Exception {
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, "SomeTest/1.0");
        newTransporter(httpServer.getHttpUrl());
        transporter.get(new GetTask(URI.create("repo/file.txt")));
        assertEquals(1, httpServer.getLogEntries().size());
        for (HttpServer.LogEntry log : httpServer.getLogEntries()) {
            assertEquals("SomeTest/1.0", log.headers.get("User-Agent"));
        }
    }

    @Test
    public void testCustomHeaders() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Custom/1.0");
        headers.put("X-CustomHeader", "Custom-Value");
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, "SomeTest/1.0");
        session.setConfigProperty(ConfigurationProperties.HTTP_HEADERS + ".test", headers);
        newTransporter(httpServer.getHttpUrl());
        transporter.get(new GetTask(URI.create("repo/file.txt")));
        assertEquals(1, httpServer.getLogEntries().size());
        for (HttpServer.LogEntry log : httpServer.getLogEntries()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                assertEquals(entry.getKey(), entry.getValue(), log.headers.get(entry.getKey()));
            }
        }
    }

    @Test
    public void testServerAuthScopeNotUsedForProxy() throws Exception {
        String username = "testuser", password = "testpass";
        httpServer.setProxyAuthentication(username, password);
        auth = new AuthenticationBuilder()
                .addUsername(username)
                .addPassword(password)
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://" + httpServer.getHost() + ":12/");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Server auth must not be used as proxy auth");
        } catch (HttpResponseException e) {
            assertEquals(407, e.getStatusCode());
        }
    }

    @Test
    public void testProxyAuthScopeNotUsedForServer() throws Exception {
        String username = "testuser", password = "testpass";
        httpServer.setAuthentication(username, password);
        Authentication auth = new AuthenticationBuilder()
                .addUsername(username)
                .addPassword(password)
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth);
        newTransporter("http://" + httpServer.getHost() + ":12/");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Proxy auth must not be used as server auth");
        } catch (HttpResponseException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    @Test
    public void testAuthSchemeReuse() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setProxyAuthentication("proxyuser", "proxypass");
        session.setCache(new DefaultRepositoryCache());
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        Authentication auth = new AuthenticationBuilder()
                .addUsername("proxyuser")
                .addPassword("proxypass")
                .build();
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth);
        newTransporter("http://bad.localhost:1/");
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(3, httpServer.getLogEntries().size());
        httpServer.getLogEntries().clear();
        newTransporter("http://bad.localhost:1/");
        task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(1, httpServer.getLogEntries().size());
        assertNotNull(httpServer.getLogEntries().get(0).headers.get("Authorization"));
        assertNotNull(httpServer.getLogEntries().get(0).headers.get("Proxy-Authorization"));
    }

    @Test
    public void testAuthSchemePreemptive() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        session.setCache(new DefaultRepositoryCache());
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();

        session.setConfigProperty(ConfigurationProperties.HTTP_PREEMPTIVE_AUTH, false);
        newTransporter(httpServer.getHttpUrl());
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        // there ARE challenge round-trips
        assertEquals(2, httpServer.getLogEntries().size());

        httpServer.getLogEntries().clear();

        session.setConfigProperty(ConfigurationProperties.HTTP_PREEMPTIVE_AUTH, true);
        newTransporter(httpServer.getHttpUrl());
        task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        // there are NO challenge round-trips, all goes through at first
        assertEquals(1, httpServer.getLogEntries().size());
    }

    @Test
    public void testConnectionReuse() throws Exception {
        httpServer.addSslConnector();
        session.setCache(new DefaultRepositoryCache());
        for (int i = 0; i < 3; i++) {
            newTransporter(httpServer.getHttpsUrl());
            GetTask task = new GetTask(URI.create("repo/file.txt"));
            transporter.get(task);
            assertEquals("test", task.getDataString());
        }
        PoolStats stats = ((ConnPoolControl<?>)
                        ((HttpTransporter) transporter).getState().getConnectionManager())
                .getTotalStats();
        assertEquals(stats.toString(), 1, stats.getAvailable());
    }

    @Test
    public void testConnectionNoReuse() throws Exception {
        httpServer.addSslConnector();
        session.setCache(new DefaultRepositoryCache());
        session.setConfigProperty(ConfigurationProperties.HTTP_REUSE_CONNECTIONS, false);
        for (int i = 0; i < 3; i++) {
            newTransporter(httpServer.getHttpsUrl());
            GetTask task = new GetTask(URI.create("repo/file.txt"));
            transporter.get(task);
            assertEquals("test", task.getDataString());
        }
        PoolStats stats = ((ConnPoolControl<?>)
                        ((HttpTransporter) transporter).getState().getConnectionManager())
                .getTotalStats();
        assertEquals(stats.toString(), 0, stats.getAvailable());
    }

    @Test(expected = NoTransporterException.class)
    public void testInitBadProtocol() throws Exception {
        newTransporter("bad:/void");
    }

    @Test(expected = NoTransporterException.class)
    public void testInitBadUrl() throws Exception {
        newTransporter("http://localhost:NaN");
    }

    @Test
    public void testInitCaseInsensitiveProtocol() throws Exception {
        newTransporter("http://localhost");
        newTransporter("HTTP://localhost");
        newTransporter("Http://localhost");
        newTransporter("https://localhost");
        newTransporter("HTTPS://localhost");
        newTransporter("HttpS://localhost");
    }

    @Test
    public void testGetRFC9457Response() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("rfc9457/file.txt")));
            fail("Expected error");
        } catch (HttpRFC9457Exception e) {
            assertEquals(403, e.getStatusCode());
            assertEquals(e.getPayload().getType(), URI.create("https://example.com/probs/out-of-credit"));
            assertEquals(403, e.getPayload().getStatus().intValue());
            assertEquals("You do not have enough credit.", e.getPayload().getTitle());
            assertEquals(
                    "Your current balance is 30, but that costs 50.",
                    e.getPayload().getDetail());
            assertEquals(e.getPayload().getInstance(), URI.create("/account/12345/msgs/abc"));
        }
    }

    @Test
    public void testGetRFC9457ResponseWithMissingFields() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("rfc9457/missing_fields.txt")));
            fail("Expected error");
        } catch (HttpRFC9457Exception e) {
            assertEquals(403, e.getStatusCode());
            assertEquals(e.getPayload().getType(), URI.create("about:blank"));
            assertNull(e.getPayload().getStatus());
            assertNull(e.getPayload().getTitle());
            assertNull(e.getPayload().getDetail());
            assertNull(e.getPayload().getInstance());
        }
    }
}
