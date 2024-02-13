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

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
import org.eclipse.aether.spi.connector.transport.http.*;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.*;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Common set of tests against Http transporter.
 */
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MethodName"})
public class HttpTransporterTest {

    protected static final Path KEY_STORE_PATH = Paths.get("target/keystore");

    protected static final Path KEY_STORE_SELF_SIGNED_PATH = Paths.get("target/keystore-self-signed");

    protected static final Path TRUST_STORE_PATH = Paths.get("target/trustStore");

    static {
        // Warning: "cross connected" with HttpServer!
        System.setProperty(
                "javax.net.ssl.trustStore", KEY_STORE_PATH.toAbsolutePath().toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "server-pwd");
        System.setProperty(
                "javax.net.ssl.keyStore", TRUST_STORE_PATH.toAbsolutePath().toString());
        System.setProperty("javax.net.ssl.keyStorePassword", "client-pwd");

        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        System.setProperty("javax.net.ssl.keyStoreType", "jks");
        System.setProperty("javax.net.debug", "all");
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

        if (!Files.isRegularFile(KEY_STORE_PATH)) {
            URL keyStoreUrl = HttpTransporterTest.class.getClassLoader().getResource("ssl/server-store");
            URL keyStoreSelfSignedUrl =
                    HttpTransporterTest.class.getClassLoader().getResource("ssl/server-store-selfsigned");
            URL trustStoreUrl = HttpTransporterTest.class.getClassLoader().getResource("ssl/client-store");

            try {
                try (InputStream keyStoreStream = keyStoreUrl.openStream();
                        InputStream keyStoreSelfSignedStream = keyStoreSelfSignedUrl.openStream();
                        InputStream trustStoreStream = trustStoreUrl.openStream()) {
                    Files.copy(keyStoreStream, KEY_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(
                            keyStoreSelfSignedStream, KEY_STORE_SELF_SIGNED_PATH, StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(trustStoreStream, TRUST_STORE_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
            httpServer.stop();
            httpServer = null;
        }
        factory = null;
        session = null;
    }

    @Test
    protected void testClassify() {
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new HttpTransporterException(403)));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new HttpTransporterException(404)));
    }

    @Test
    protected void testPeek() throws Exception {
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
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
        httpServer.addSslConnector();
        newTransporter(httpServer.getHttpsUrl());
        transporter.peek(new PeekTask(URI.create("repo/file.txt")));
    }

    @Test
    protected void testPeek_Redirect() throws Exception {
        httpServer.addSslConnector();
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
        assertEquals(file.lastModified(), OLD_FILE_TIMESTAMP);
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
    protected void testGet_SSL() throws Exception {
        httpServer.addSslConnector();
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
    }

    @Test
    protected void testGet_SSL_WithServerErrors() throws Exception {
        httpServer.setServerErrorsBeforeWorks(1);
        httpServer.addSslConnector();
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
        httpServer.addSelfSignedSslConnector();
        try {
            newTransporter(httpServer.getHttpsUrl());
            fail("Unsupported security mode");
        } catch (IllegalArgumentException a) {
            // good
        }
    }

    @Test
    protected void testGet_HTTPS_Insecure_SecurityMode() throws Exception {
        // here we use alternate server-store-selfigned key (as the key set it static initalizer is probably already
        // used to init SSLContext/SSLSocketFactory/etc
        session.setConfigProperty(
                ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
        httpServer.addSelfSignedSslConnector();
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
    }

    @Test
    protected void testGet_Redirect() throws Exception {
        httpServer.addSslConnector();
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
    }

    @Test
    protected void testPut_EmptyResource() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("repo/file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.getDataOffset());
        assertEquals(0L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
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
        assertEquals(1, listener.getStartedCount());
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
        assertEquals(1, listener.getStartedCount());
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
        assertEquals(1, listener.getStartedCount());
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
        assertEquals(1, listener.getStartedCount()); // w/ expectContinue enabled would have here 2
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
        assertEquals(1, listener.getStartedCount());
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
        assertEquals(0L, listener.getDataOffset());
        assertEquals(6L, listener.getDataLength());
        assertEquals(1, listener.getStartedCount());
        assertTrue(listener.getProgressedCount() > 0, "Count: " + listener.getProgressedCount());
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
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
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
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
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
        httpServer.getLogEntries().clear();
        task = new PutTask(URI.create("repo/file.txt")).setDataString("upload");
        transporter.put(task);
        assertEquals(1, httpServer.getLogEntries().size()); // put w/ auth
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
            assertEquals("SomeTest/1.0", log.getHeaders().get("User-Agent"));
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
                assertEquals(entry.getValue(), log.getHeaders().get(entry.getKey()), entry.getKey());
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
        assertNotNull(httpServer.getLogEntries().get(0).getHeaders().get("Authorization"));
        assertNotNull(httpServer.getLogEntries().get(0).getHeaders().get("Proxy-Authorization"));
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
        // there are NO challenge round-trips, all goes through at first
        assertEquals(1, httpServer.getLogEntries().size());
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
