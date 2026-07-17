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
package org.eclipse.aether.internal.test.util.http;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.internal.impl.transport.http.DefaultChecksumExtractor;
import org.eclipse.aether.internal.impl.transport.http.Nx2ChecksumExtractor;
import org.eclipse.aether.internal.impl.transport.http.XChecksumExtractor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLocalRepositoryManager;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractorStrategy;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterFactory;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.HttpRFC9457Exception;
import org.eclipse.aether.transfer.HttpTransportProperty;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.jetty.http.HttpVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Common set of tests against Http transporter.
 */
@SuppressWarnings({"checkstyle:MethodName"})
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class HttpTransporterTest {

    protected static final Path SERVER_STORE_PATH = Paths.get("target/server-store");

    protected static final Path CLIENT_STORE_PATH = Paths.get("target/client-store");

    protected static final Path PEM_QUICHE_SERVER_PATH = Paths.get("target/pems");

    protected static SSLContext defaultSslContext;

    static {
        // uncomment to enable SSL debugging for easier troubleshooting of SSL related test failures
        // System.setProperty("javax.net.debug", "all");
    }

    @BeforeAll
    protected static void beforeAll() throws NoSuchAlgorithmException {
        // populate custom keystore and truststore
        try {
            try (InputStream keyStoreStream =
                            HttpTransporterTest.class.getClassLoader().getResourceAsStream("ssl/server-store");
                    InputStream trustStoreStream =
                            HttpTransporterTest.class.getClassLoader().getResourceAsStream("ssl/client-store"); ) {
                Files.copy(keyStoreStream, SERVER_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(trustStoreStream, CLIENT_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                Files.createDirectories(PEM_QUICHE_SERVER_PATH);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // override default SSLContext to include our custom keystore and truststore (which are "cross connected" with
        // HttpServer)
        defaultSslContext = SSLContext.getDefault();
        SSLContext.setDefault(createClientSSLContext());
    }

    @AfterAll
    protected static void afterAll() {
        if (defaultSslContext != null) {
            SSLContext.setDefault(defaultSslContext);
        }
    }

    /**
     * Creates an {@link SSLContext} for the client that extends the default keystore and truststore with the entries
     * from {@link #SERVER_STORE_PATH} (password {@code "server-pwd"}) and {@link #CLIENT_STORE_PATH}
     * (password {@code "client-pwd"}).
     *
     * @return an {@link SSLContext} combining default and custom key/trust material
     */
    protected static SSLContext createClientSSLContext() {
        try {
            // Load custom key store (KEY_STORE_PATH acts as truststore in "cross connected" setup)
            KeyStore customTrustStore = KeyStore.getInstance("pkcs12");
            try (InputStream is = Files.newInputStream(SERVER_STORE_PATH)) {
                customTrustStore.load(is, "server-pwd".toCharArray());
            }

            // Load custom trust store (TRUST_STORE_PATH acts as keystore in "cross connected" setup)
            KeyStore customKeyStore = KeyStore.getInstance("pkcs12");
            try (InputStream is = Files.newInputStream(CLIENT_STORE_PATH)) {
                customKeyStore.load(is, "client-pwd".toCharArray());
            }

            // Load default truststore and merge custom entries
            KeyStore defaultTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path defaultTrustStorePath = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            if (Files.exists(defaultTrustStorePath)) {
                try (InputStream is = Files.newInputStream(defaultTrustStorePath)) {
                    defaultTrustStore.load(is, "changeit".toCharArray());
                }
            } else {
                defaultTrustStore.load(null, null);
            }
            Enumeration<String> aliases = customTrustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                defaultTrustStore.setCertificateEntry("custom-trust-" + alias, customTrustStore.getCertificate(alias));
            }

            // Load default keystore and merge custom entries
            KeyStore mergedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            mergedKeyStore.load(null, null);
            Enumeration<String> keyAliases = customKeyStore.aliases();
            while (keyAliases.hasMoreElements()) {
                String alias = keyAliases.nextElement();
                if (customKeyStore.isKeyEntry(alias)) {
                    mergedKeyStore.setKeyEntry(
                            alias,
                            customKeyStore.getKey(alias, "client-pwd".toCharArray()),
                            "client-pwd".toCharArray(),
                            customKeyStore.getCertificateChain(alias));
                } else {
                    mergedKeyStore.setCertificateEntry(alias, customKeyStore.getCertificate(alias));
                }
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(defaultTrustStore);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(mergedKeyStore, "client-pwd".toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    private final Supplier<HttpTransporterFactory> transporterFactorySupplier;

    protected DefaultRepositorySystemSession session;

    protected HttpTransporterFactory factory;

    protected HttpTransporter transporter;

    protected Runnable closer;

    protected File repoDir;

    protected HttpServer httpServer;

    protected Authentication auth;

    protected Proxy proxy;

    protected HttpTransporterTest(Supplier<HttpTransporterFactory> transporterFactorySupplier) {
        this.transporterFactorySupplier = requireNonNull(transporterFactorySupplier);
    }

    protected static ChecksumExtractor standardChecksumExtractor() {
        HashMap<String, ChecksumExtractorStrategy> strategies = new HashMap<>();
        strategies.put("1", new Nx2ChecksumExtractor());
        strategies.put("2", new XChecksumExtractor());
        return new DefaultChecksumExtractor(strategies);
    }

    protected RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("test", "default", url)
                .setAuthentication(auth)
                .setProxy(proxy)
                .build();
    }

    protected void newTransporter(String url) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (closer != null) {
            closer.run();
            closer = null;
        }
        session = new DefaultRepositorySystemSession(session);
        session.setData(new DefaultSessionData());
        transporter = factory.newInstance(session, newRepo(url));
    }

    protected static final long OLD_FILE_TIMESTAMP = 160660800000L;

    /** HTTP status code for "Too Many Requests". */
    private static final int SC_TOO_MANY_REQUESTS = 429;

    @BeforeEach
    protected void setUp(TestInfo testInfo) throws Exception {
        System.out.println("=== " + testInfo.getDisplayName() + " ===");
        session = new DefaultRepositorySystemSession(h -> {
            this.closer = h;
            return true;
        });
        session.setLocalRepositoryManager(new TestLocalRepositoryManager());
        factory = transporterFactorySupplier.get();
        repoDir = TestFileUtils.createTempDir();
        TestFileUtils.writeString(new File(repoDir, "file.txt"), "test");
        TestFileUtils.writeString(new File(repoDir, "artifact.pom"), "<xml>pom</xml>");
        TestFileUtils.writeString(new File(repoDir, "dir/file.txt"), "test");
        TestFileUtils.writeString(new File(repoDir, "dir/oldFile.txt"), "oldTest", OLD_FILE_TIMESTAMP);
        TestFileUtils.writeString(new File(repoDir, "empty.txt"), "");
        TestFileUtils.writeString(new File(repoDir, "some space.txt"), "space");
        try (InputStream is = getCompressibleFileStream()) {
            Files.copy(is, repoDir.toPath().resolve("compressible-file.xml"));
        }
        File resumable = new File(repoDir, "resume.txt");
        TestFileUtils.writeString(resumable, "resumable");
        resumable.setLastModified(System.currentTimeMillis() - 90 * 1000);
        httpServer = new HttpServer().setRepoDir(repoDir).start();
        // always create a transporter connecting to the Http URL
        newTransporter(httpServer.getHttpUrl());
    }

    private static InputStream getCompressibleFileStream() {
        return HttpTransporterTest.class.getClassLoader().getResourceAsStream("compressible-file.xml");
    }

    @AfterEach
    protected void tearDown() throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (closer != null) {
            closer.run();
            closer = null;
        }
        if (httpServer != null) {
            // check for leaked connections (e.g., due to not closing response body streams)
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> httpServer.getNumConnectedEndPoints() == 0);
            httpServer.stop();
            httpServer = null;
        }
        factory = null;
        session = null;
    }

    /**
     * Indicates whether the transporter implementation supports preemptive authentication (i.e., sending credentials with the first request).
     * @return {@code true} if preemptive authentication is supported, {@code false} otherwise.
     */
    protected boolean supportsPreemptiveAuth() {
        return true;
    }

    /**
     * Indicates whether the transporter implementation supports HTTP/3.
     * @return {@code true} if HTTP/3 is supported, {@code false} otherwise.
     */
    protected boolean supportsHttp3() {
        // skip on ASF Jenkins due to incompatible GLIBC version
        // (https://github.com/jetty-project/jetty-quiche-native/issues/180 and
        // https://issues.apache.org/jira/browse/INFRA-28128)
        // identified via property "os.version" exposed in https://ci-maven.apache.org/computer/maven6/systemInfo
        assumeFalse(
                System.getProperty("os.version").equals("5.15.0-1089-azure"),
                "Skipping HTTP/3 tests on ASF Jenkins Linux Nodes");
        return true;
    }

    /**
     * Indicates whether the transporter implementation supports HTTP/2.
     * @return {@code true} if HTTP/2 is supported, {@code false} otherwise.
     */
    protected boolean supportsHttp2() {
        return true;
    }

    @Test
    protected void testClassify() {
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new HttpTransporterException(403)));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new HttpTransporterException(404)));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new HttpTransporterException(410)));
    }

    @Test
    protected void testPeek() throws Exception {
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    protected void testPeek_DoesNotAcceptRfc9457() throws Exception {
        // peek is HEAD request, therefore cannot support RFC 9457
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
        String accept = httpServer.getLogEntries().get(0).getRequestHeaders().get("Accept");
        assertNull(accept, "No accept header expected for HEAD request, but was: " + accept);
    }

    @Test
    protected void testRetryHandler_defaultCount_positive() throws Exception {
        httpServer.setConnectionsToClose(3);
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    protected void testRetryHandler_defaultCount_negative() throws Exception {
        httpServer.setConnectionsToClose(4);
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (Exception expected) {
        }
    }

    @Test
    protected void testRetryHandler_tooManyRequests_explicitCount_positive() throws Exception {
        // set low retry count as this involves back off delays
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 1);
        int retryIntervalMs = 500;
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, retryIntervalMs);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setServerErrorsBeforeWorks(1, SC_TOO_MANY_REQUESTS);
        long startTime = System.currentTimeMillis();
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
        assertTrue(
                System.currentTimeMillis() - startTime >= retryIntervalMs,
                "Expected back off delay of at least " + retryIntervalMs);
    }

    @Test
    protected void testRetryHandler_tooManyRequests_explicitCount_negative() throws Exception {
        // set low retry count as this involves back off delays
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 3);
        int retryIntervalMs = 100;
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, retryIntervalMs);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setServerErrorsBeforeWorks(4, SC_TOO_MANY_REQUESTS);
        long startTime = System.currentTimeMillis();
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (Exception expected) {
        }
        // linear backoff: 1x + 2x + 3x
        long expectedMinimumDuration = retryIntervalMs * (1 + 2 + 3);
        assertTrue(
                System.currentTimeMillis() - startTime >= expectedMinimumDuration,
                "Expected back off delay of at least " + expectedMinimumDuration);
    }

    @Test
    protected void testRetryHandler_explicitCount_positive() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 10);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setConnectionsToClose(10);
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    protected void testRetryHandler_disabled() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, 0);
        newTransporter(httpServer.getHttpUrl());
        httpServer.setConnectionsToClose(1);
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
        } catch (Exception expected) {
        }
    }

    @Test
    protected void testPeek_NotFound() throws Exception {
        try {
            transporter.peek(new PeekTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    protected void testPeek_Closed() throws Exception {
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testPeek_Authenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    protected void testPeek_Unauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testPeek_ProxyAuthenticated() throws Exception {
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
    protected void testPeek_ProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        try {
            transporter.peek(new PeekTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testPeek_SSL() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        PeekTask task = new PeekTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.peek(task);
        assertEquals(
                HttpTransportProperty.SslProtocol.TLS_1_3,
                listener.getTransportProperties().get(HttpTransportProperty.Key.SSL_PROTOCOL));
    }

    @Test
    protected void testPeek_Redirect() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        transporter.peek(new PeekTask(URI.create("redirect/file.txt")));
        transporter.peek(new PeekTask(URI.create("redirect/file.txt?scheme=https")));
    }

    @Test
    protected void testGet_ToMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_ToFile() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt"))
                .setDataPath(file.toPath())
                .setListener(listener);
        transporter.get(task);
        assertEquals("test", TestFileUtils.readString(file));
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("test", listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_ToFileTimestamp() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/dir/oldFile.txt"))
                .setDataPath(file.toPath())
                .setListener(listener);
        transporter.get(task);
        assertEquals("oldTest", TestFileUtils.readString(file));
        assertEquals(0L, listener.getDataOffset());
        assertEquals(7L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("oldTest", listener.getBaos().toString(StandardCharsets.UTF_8));
        assertEquals(OLD_FILE_TIMESTAMP, file.lastModified());
    }

    @Test
    protected void testGet_AcceptsRfc9457() throws Exception {
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        String accept = httpServer.getLogEntries().get(0).getRequestHeaders().get("Accept");
        assertNotNull(accept, "Missing Accept header when retrieving artifact");
        assertTrue(
                accept.contains("application/problem+json"),
                "Expected Accept header to contain application/problem+json, but was: " + accept);
    }

    @Test
    protected void testGet_ParseRfc9457() throws Exception {
        // use Maven Central (Cloudflare CDN) as endpoints that return RFC 9457 responses
        newTransporter("https://repo.maven.apache.org");
        try {
            // https://blog.cloudflare.com/rfc-9457-agent-error-pages/#how-to-use-it
            GetTask task = new GetTask(URI.create("cdn-cgi/error/1020"));
            transporter.get(task);
            fail("Should have throw HttpRFC9457Exception");
        } catch (HttpRFC9457Exception e) {
            // Expected exception, verify the content of the RFC 9457 message.
            assertEquals(403, e.getStatusCode());
            assertEquals("Error 1020: Access denied", e.getPayload().getTitle());
            assertEquals(
                    "The request was blocked by a Cloudflare firewall rule configured by the site owner.",
                    e.getPayload().getDetail());
        }
    }

    /**
     * Provides compression algorithms supported by the transporter implementation.
     * This should be the string value passed in the {@code Accept-Encoding} header.
     *
     * @return stream of supported compression algorithm names
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Accept-Encoding#directives">Accept-Encoding directives</a>
     */
    protected abstract Stream<String> supportedCompressionAlgorithms();

    protected boolean exposeContentCodingInTransportProperties() {
        return true;
    }

    @ParameterizedTest
    // DEFLATE isn't supported by Jetty server (https://github.com/jetty/jetty.project/issues/280)
    @ValueSource(strings = {"br", "gzip", "zstd"})
    protected void testGet_WithCompression(String encoding) throws Exception {
        assumeTrue(
                supportedCompressionAlgorithms().anyMatch(supported -> supported.equals(encoding)),
                () -> "Transporter does not support compression algorithm: " + encoding);
        RecordingTransportListener listener = new RecordingTransportListener();
        // requires a file with at least 48/50 bytes (otherwise compression is disabled,
        // https://github.com/jetty/jetty.project/blob/2264d3d9f9586f3e5e9040fba779ed72e931cb46/jetty-core/jetty-compression/jetty-compression-brotli/src/main/java/org/eclipse/jetty/compression/brotli/BrotliCompression.java#L61)
        GetTask task = new GetTask(URI.create(encoding + "/repo/compressible-file.xml")).setListener(listener);
        transporter.get(task);
        String acceptEncoding =
                httpServer.getLogEntries().get(0).getRequestHeaders().get("Accept-Encoding");
        assertNotNull(acceptEncoding, "Missing Accept-Encoding header when retrieving pom");
        assertTrue(acceptEncoding.contains(encoding));
        // check original response header sent by server (client transparently handles compression and removes it)
        // see https://issues.apache.org/jira/browse/HTTPCORE-792
        // and https://github.com/mizosoft/methanol/issues/182
        for (HttpServer.LogEntry log : httpServer.getLogEntries()) {
            assertEquals(encoding, log.getResponseHeaders().get("Content-Encoding"));
        }
        String expectedResourceData;
        try (InputStream is = getCompressibleFileStream()) {
            expectedResourceData = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(expectedResourceData, task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        // data length is unknown as chunked transfer encoding is used with compression
        assertEquals(-1, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        if (exposeContentCodingInTransportProperties()) {
            assertEquals(encoding, listener.getTransportProperties().get(HttpTransportProperty.Key.CONTENT_CODING));
        }
    }

    @Test
    protected void testGet_EmptyResource() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/empty.txt"))
                .setDataPath(file.toPath())
                .setListener(listener);
        transporter.get(task);
        assertEquals("", TestFileUtils.readString(file));
        assertEquals(0L, listener.getDataOffset());
        assertEquals(0L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertEquals(0, listener.getProgressedCount());
        assertEquals("", listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_EncodedResourcePath() throws Exception {
        GetTask task = new GetTask(URI.create("repo/some%20space.txt"));
        transporter.get(task);
        assertEquals("space", task.getDataString());
    }

    @Test
    protected void testGet_Authenticated() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_Unauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testGet_ProxyAuthenticated() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_ProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testGet_RFC9457Response() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("rfc9457/file.txt")));
            fail("Expected error");
        } catch (HttpRFC9457Exception e) {
            assertEquals(403, e.getStatusCode());
            assertEquals(e.getPayload().getType(), URI.create("https://example.com/probs/out-of-credit"));
            assertEquals(403, e.getPayload().getStatus());
            assertEquals("You do not have enough credit.", e.getPayload().getTitle());
            assertEquals(
                    "Your current balance is 30, but that costs 50.",
                    e.getPayload().getDetail());
            assertEquals(URI.create("/account/12345/msgs/abc"), e.getPayload().getInstance());
        }
    }

    @Test
    protected void testGet_RFC9457Response_with_missing_fields() throws Exception {
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

    @Test
    protected void testGet_SSL() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        assertEquals(
                HttpTransportProperty.SslProtocol.TLS_1_3,
                listener.getTransportProperties().get(HttpTransportProperty.Key.SSL_PROTOCOL));
        assertEquals(
                "TLS_AES_256_GCM_SHA384",
                listener.getTransportProperties().get(HttpTransportProperty.Key.SSL_CIPHER_SUITE));
    }

    @Test
    protected void testGet_SSL_WithServerErrors() throws Exception {
        httpServer.setServerErrorsBeforeWorks(1);
        httpServer.addHttp2ConnectorWithMutualTLS();
        newTransporter(httpServer.getHttpsUrl());
        for (int i = 1; i < 3; i++) {
            try {
                RecordingTransportListener listener = new RecordingTransportListener();
                GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
                transporter.get(task);
                assertEquals("test", task.getDataString());
                assertEquals(0L, listener.getDataOffset());
                assertEquals(4L, listener.getDataLength());
                assertEquals(1, listener.getStartedCount());
                assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
                assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
            } catch (HttpTransporterException e) {
                assertEquals(500, e.getStatusCode());
            }
        }
    }

    @Test
    protected void testGet_HTTPS_Unknown_SecurityMode() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTPS_SECURITY_MODE, "unknown");
        httpServer.addHttp2Connector();
        try {
            newTransporter(httpServer.getHttpsUrl());
            fail("Unsupported security mode");
        } catch (IllegalArgumentException a) {
            // good
        }
    }

    @Test
    protected void testGet_HTTPS_Insecure_SecurityMode() throws Exception {
        // we have to reset the default ssl context to avoid the default truststore being used, which would make the
        // test pass even if the security mode is not set to insecure
        SSLContext.setDefault(defaultSslContext);
        try {
            session.setConfigProperty(
                    ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
            httpServer.addHttp2Connector();
            newTransporter(httpServer.getHttpsUrl());
            RecordingTransportListener listener = new RecordingTransportListener();
            GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
            transporter.get(task);
            assertEquals("test", task.getDataString());
            assertEquals(0L, listener.getDataOffset());
            assertEquals(4L, listener.getDataLength());
            assertEquals(1, listener.getStartedCount());
            assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
            assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        } finally {
            // restore the default SSL context used for all other tests
            SSLContext.setDefault(createClientSSLContext());
        }
    }

    @Test
    protected void testGet_HTTPS_HTTP2Only_Insecure_SecurityMode() throws Exception {
        assumeTrue(supportsHttp2(), "Transporter does not support HTTP/2");
        // we have to reset the default ssl context to avoid the default truststore being used, which would make the
        // test pass even if the security mode is not set to insecure
        SSLContext.setDefault(defaultSslContext);
        try {
            session.setConfigProperty(ConfigurationProperties.HTTP_VERSION, ConfigurationProperties.HttpVersion.HTTP_2);
            session.setConfigProperty(
                    ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
            httpServer.addHttp2OnlyConnector();
            newTransporter(httpServer.getHttpsUrl());
            RecordingTransportListener listener = new RecordingTransportListener();
            GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
            transporter.get(task);
            assertEquals("test", task.getDataString());
            assertEquals(0L, listener.getDataOffset());
            assertEquals(4L, listener.getDataLength());
            assertEquals(1, listener.getStartedCount());
            assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
            assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
            httpServer.getLogEntries().forEach(log -> {
                assertEquals(HttpVersion.HTTP_2, log.getVersion());
            });
        } finally {
            // restore the default SSL context used for all other tests
            SSLContext.setDefault(createClientSSLContext());
        }
    }

    @Test
    protected void testGet_HTTP3Only() throws Exception {
        assumeTrue(supportsHttp3(), "Transporter does not support HTTP/3");
        session.setConfigProperty(ConfigurationProperties.HTTP_VERSION, ConfigurationProperties.HttpVersion.HTTP_3);
        httpServer.addHttp3Connector(false);
        httpServer.start();
        newTransporter(httpServer.getHttp3Url());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        httpServer.getLogEntries().forEach(log -> {
            assertEquals(HttpVersion.HTTP_3, log.getVersion());
        });
    }

    @Test
    protected void testGet_HTTP3() throws Exception {
        assumeTrue(supportsHttp3(), "Transporter does not support HTTP/3");
        session.setConfigProperty(ConfigurationProperties.HTTP_VERSION, ConfigurationProperties.HttpVersion.HTTP_3);
        // both HTTP2 and HTTP3 endpoints are available at the same port
        httpServer.addHttp2OnlyConnectorWithMutualTLS();
        httpServer.addHttp3Connector(false, httpServer.getHttpsPort());
        // alt-svc header should point to http/3
        httpServer.start();
        newTransporter(httpServer.getHttp3Url());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        // issue 2 requests to ensure that the second request is HTTP/3 (the first one may be HTTP/2 if the TCP
        // connection is faster)
        transporter.get(task);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        // the last request should be HTTP/3 finally
        assertEquals(
                HttpVersion.HTTP_3,
                httpServer
                        .getLogEntries()
                        .get(httpServer.getLogEntries().size() - 1)
                        .getVersion());
    }

    @Test
    protected void testGet_HTTP3FallbackToHTTP2() throws Exception {
        assumeTrue(supportsHttp3(), "Transporter does not support HTTP/3");
        session.setConfigProperty(ConfigurationProperties.HTTP_VERSION, ConfigurationProperties.HttpVersion.HTTP_3);
        httpServer.addHttp2OnlyConnectorWithMutualTLS();
        httpServer.start();
        newTransporter(httpServer.getHttpsUrl());
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
        assertEquals(
                HttpTransportProperty.HttpVersion.HTTP_2,
                listener.getTransportProperties().get(HttpTransportProperty.Key.HTTP_VERSION));
        httpServer.getLogEntries().forEach(log -> {
            assertEquals(HttpVersion.HTTP_2, log.getVersion());
        });
    }

    @Test
    protected void testGet_Redirect() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("redirect/file.txt?scheme=https")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals(task.getDataString(), listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_Resume() throws Exception {
        File file = TestFileUtils.createTempFile("re");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataPath(file.toPath(), true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.getStartedCount());
        assertEquals(2L, listener.getDataOffset());
        assertEquals(9, listener.getDataLength());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("sumable", listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_ResumeLocalContentsOutdated() throws Exception {
        File file = TestFileUtils.createTempFile("re");
        file.setLastModified(System.currentTimeMillis() - 5 * 60 * 1000);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataPath(file.toPath(), true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.getStartedCount());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(9, listener.getDataLength());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("resumable", listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_ResumeRangesNotSupportedByServer() throws Exception {
        httpServer.setRangeSupport(false);
        File file = TestFileUtils.createTempFile("re");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("repo/resume.txt"))
                .setDataPath(file.toPath(), true)
                .setListener(listener);
        transporter.get(task);
        assertEquals("resumable", TestFileUtils.readString(file));
        assertEquals(1L, listener.getStartedCount());
        assertEquals(0L, listener.getDataOffset());
        assertEquals(9, listener.getDataLength());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("resumable", listener.getBaos().toString(StandardCharsets.UTF_8));
    }

    @Test
    protected void testGet_Checksums_Nexus() throws Exception {
        httpServer.setChecksumHeader(HttpServer.ChecksumHeader.NEXUS);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(
                "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", task.getChecksums().get("SHA-1"));
    }

    @Test
    protected void testGet_Checksums_XChecksum() throws Exception {
        httpServer.setChecksumHeader(HttpServer.ChecksumHeader.XCHECKSUM);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(
                "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", task.getChecksums().get("SHA-1"));
    }

    @Test
    protected void testGet_FileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File file = TestFileUtils.createTempFile("failure");
            transporter.get(new GetTask(URI.create("repo/file.txt")).setDataPath(file.toPath()));
            assertTrue(file.delete(), i + ", " + file.getAbsolutePath());
        }
    }

    @Test
    protected void testGet_NotFound() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    protected void testGet_Closed() throws Exception {
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testGet_StartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertEquals(0, listener.getProgressedCount());
    }

    @Test
    protected void testGet_ProgressCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress();
        GetTask task = new GetTask(URI.create("repo/file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.getDataOffset());
        assertEquals(4L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertEquals(1, listener.getProgressedCount());
    }

    @Test
    protected void testPut_FromMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        String payload = "upload";
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString(payload);
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
        assertEquals(
                String.valueOf(payload.getBytes(StandardCharsets.UTF_8).length),
                httpServer.getLogEntries().get(0).getRequestHeaders().get("Content-Length"));
    }

    @Test
    protected void testPut_AcceptsRfc9457() throws Exception {
        String payload = "upload";
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString(payload);
        transporter.put(task);
        String accept = httpServer.getLogEntries().get(0).getRequestHeaders().get("Accept");
        assertNotNull(accept, "Missing Accept header when retrieving artifact");
        assertTrue(
                accept.contains("application/problem+json"),
                "Expected Accept header to contain application/problem+json, but was: " + accept);
    }

    @Test
    protected void testPut_FromFile() throws Exception {
        File file = TestFileUtils.createTempFile("upload");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataPath(file.toPath());
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
        assertEquals(
                String.valueOf(file.length()),
                httpServer.getLogEntries().get(0).getRequestHeaders().get("Content-Length"));
    }

    @Test
    protected void testPut_EmptyResource() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(0L, listener.getDataLength());
        // some transports may skip the upload for empty resources
        assertTrue(
                listener.getStartedCount() <= 1,
                "The transport should be started at most once but was started " + listener.getStartedCount()
                        + " times");
        assertEquals(0, listener.getProgressedCount());
        assertEquals("", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_EncodedResourcePath() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/some%20space.txt"))
                .setListener(listener)
                .setDataString("OK");
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(2L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("OK", TestFileUtils.readString(new File(repoDir, "some space.txt")));
    }

    @Test
    protected void testPut_Authenticated_ExpectContinue() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(supportsPreemptiveAuth() ? 1 : 2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_Authenticated_ExpectContinueBroken() throws Exception {
        // this makes OPTIONS recover, and have only 1 PUT (startedCount=1 as OPTIONS is not counted)
        session.setConfigProperty(ConfigurationProperties.HTTP_SUPPORT_WEBDAV, true);
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(supportsPreemptiveAuth() ? 1 : 2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_Authenticated_ExpectContinueRejected() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(supportsPreemptiveAuth() ? 1 : 2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_Authenticated_ExpectContinueDisabled() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(
                supportsPreemptiveAuth() ? 1 : 2,
                listener.getStartedCount()); // w/ expectContinue enabled would have here 2/3
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_Unauthenticated() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(401, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0, listener.getStartedCount());
        assertEquals(0, listener.getProgressedCount());
    }

    @Test
    protected void testPut_ProxyAuthenticated() throws Exception {
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(supportsPreemptiveAuth() ? 1 : 2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    protected void testPut_ProxyUnauthenticated() throws Exception {
        httpServer.setProxyAuthentication("testuser", "testpass");
        proxy = new Proxy(Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort());
        newTransporter("http://bad.localhost:1/");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (HttpTransporterException e) {
            assertEquals(407, e.getStatusCode());
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0, listener.getStartedCount());
        assertEquals(0, listener.getProgressedCount());
    }

    @Test
    protected void testPut_SSL() throws Exception {
        httpServer.addHttp2ConnectorWithMutualTLS();
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(supportsPreemptiveAuth() ? 1 : 2, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
        assertEquals(
                HttpTransportProperty.SslProtocol.TLS_1_3,
                listener.getTransportProperties().get(HttpTransportProperty.Key.SSL_PROTOCOL));
    }

    @Test
    protected void testPut_FileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File src = TestFileUtils.createTempFile("upload");
            File dst = new File(repoDir, "file.txt");
            transporter.put(new PutTask(URI.create("repo/file.txt")).setDataPath(src.toPath()));
            assertTrue(src.delete(), i + ", " + src.getAbsolutePath());
            assertTrue(dst.delete(), i + ", " + dst.getAbsolutePath());
        }
    }

    @Test
    protected void testPut_Closed() throws Exception {
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("repo/missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    protected void testPut_StartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertEquals(0, listener.getProgressedCount());
    }

    @Test
    protected void testPut_ProgressCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress();
        PutTask task =
                new PutTask(URI.create("repo/file.txt")).setListener(listener).setDataString("upload");
        try {
            transporter.put(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertEquals(1, listener.getProgressedCount());
    }

    @Test
    protected void testGetPut_AuthCache() throws Exception {
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
        assertEquals(1, listener.getStartedCount());
    }

    @Test
    protected void testPut_PreemptiveIsDefault() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(
                supportsPreemptiveAuth() ? 1 : 2, httpServer.getLogEntries().size()); // put w/ auth
    }

    @Test
    protected void testPut_AuthCache() throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_PREEMPTIVE_PUT_AUTH, false);
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
    protected void testPut_AuthCache_Preemptive() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        session.setConfigProperty(ConfigurationProperties.HTTP_PREEMPTIVE_AUTH, true);
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(
                supportsPreemptiveAuth() ? 1 : 2, httpServer.getLogEntries().size()); // put w/ auth
        httpServer.getLogEntries().clear();
        task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
    }

    @Test
    protected void testPut_WithResponseBody() throws Exception {
        httpServer.setAuthentication("testuser", "testpass");
        httpServer.setResponseBodyForPut("Some dummy response body");
        auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        newTransporter(httpServer.getHttpUrl());
        PutTask task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        // this leads to stuck threads in some transporters if the response body is not consumed
    }

    @Test
    @Timeout(20)
    protected void testConcurrency() throws Exception {
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
            threads[i] = new Thread(() -> {
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
            });
            threads[i].setName("Task-" + i);
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertNull(error.get(), String.valueOf(error.get()));
    }

    @Test
    @Timeout(10)
    protected void testConnectTimeout() throws Exception {
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, 100);
        int port = 1;
        newTransporter("http://localhost:" + port);
        try {
            transporter.get(new GetTask(URI.create("repo/file.txt")));
            fail("Expected error");
        } catch (Exception e) {
            // impl specific "timeout" exception
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    @Timeout(10)
    protected void testRequestTimeout() throws Exception {
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, 100);
        ServerSocket server = new ServerSocket(0);
        try (server) {
            newTransporter("http://localhost:" + server.getLocalPort());
            try {
                transporter.get(new GetTask(URI.create("repo/file.txt")));
                fail("Expected error");
            } catch (Exception e) {
                assertTrue(e.getClass().getSimpleName().contains("Timeout"));
                assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
            }
        }
    }

    @Test
    protected void testUserAgent() throws Exception {
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, "SomeTest/1.0");
        newTransporter(httpServer.getHttpUrl());
        transporter.get(new GetTask(URI.create("repo/file.txt")));
        assertEquals(1, httpServer.getLogEntries().size());
        for (HttpServer.LogEntry log : httpServer.getLogEntries()) {
            assertEquals("SomeTest/1.0", log.getRequestHeaders().get("User-Agent"));
        }
    }

    @Test
    protected void testCustomHeaders() throws Exception {
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
                assertEquals(entry.getValue(), log.getRequestHeaders().get(entry.getKey()), entry.getKey());
            }
        }
    }

    @Test
    protected void testServerAuthScope_NotUsedForProxy() throws Exception {
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
        } catch (HttpTransporterException e) {
            assertEquals(407, e.getStatusCode());
        } catch (IOException e) {
            // accepted as well: point is to fail
        }
    }

    @Test
    protected void testProxyAuthScope_NotUsedForServer() throws Exception {
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
        } catch (HttpTransporterException e) {
            assertEquals(401, e.getStatusCode());
        } catch (IOException e) {
            // accepted as well: point is to fail
        }
    }

    @Test
    protected void testAuthSchemeReuse() throws Exception {
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
        assertNotNull(httpServer.getLogEntries().get(0).getRequestHeaders().get("Authorization"));
        assertNotNull(httpServer.getLogEntries().get(0).getRequestHeaders().get("Proxy-Authorization"));
    }

    @Test
    protected void testAuthSchemePreemptive() throws Exception {
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
        // there are (potentially) NO challenge round-trips, all goes through at first
        assertEquals(
                supportsPreemptiveAuth() ? 1 : 2, httpServer.getLogEntries().size());
    }

    @Test
    void testInit_BadProtocol() {
        assertThrows(NoTransporterException.class, () -> newTransporter("bad:/void"));
    }

    @Test
    void testInit_BadUrl() {
        assertThrows(NoTransporterException.class, () -> newTransporter("http://localhost:NaN"));
    }

    @Test
    void testInit_CaseInsensitiveProtocol() throws Exception {
        newTransporter("http://localhost");
        newTransporter("HTTP://localhost");
        newTransporter("Http://localhost");
        newTransporter("https://localhost");
        newTransporter("HTTPS://localhost");
        newTransporter("HttpS://localhost");
    }
}
