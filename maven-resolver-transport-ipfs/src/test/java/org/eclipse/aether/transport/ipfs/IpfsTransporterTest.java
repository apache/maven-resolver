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
package org.eclipse.aether.transport.ipfs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 */
@Testcontainers(disabledWithoutDocker = true)
public class IpfsTransporterTest {
    public static final DockerImageName IPFS_KUBO_IMAGE = DockerImageName.parse("ipfs/kubo:release");

    public static GenericContainer<?> kubo = new GenericContainer<>(IPFS_KUBO_IMAGE)
            .withReuse(false)
            .withAccessToHost(false)
            .withExposedPorts(5001);
    public static int kuboPort;

    @BeforeAll
    static void startContainer() {
        kubo.start();
        kuboPort = kubo.getMappedPort(5001);
    }

    @AfterAll
    static void stopContainer() {
        kubo.stop();
    }

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private Path tempDir;

    enum UseCase {
        CID("ipfs:/QmSnuWmxptJZdLJpKRarxBMS2Ju2oANVrgbr2xWbie9b2D"),
        CID_WITH_PREFIX("ipfs:/QmSnuWmxptJZdLJpKRarxBMS2Ju2oANVrgbr2xWbie9b2D/repository"),
        NS("ipfs:/org.apache"),
        NS_WITH_PREFIX("ipfs:/org.apache/repository");

        final String remoteRepositoryUri;

        UseCase(String remoteRepositoryUri) {
            this.remoteRepositoryUri = remoteRepositoryUri;
        }
    }

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("testtest", "default", url).build();
    }

    private void newTransporter(String url) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (factory == null) {
            factory = new IpfsTransporterFactory();
        }
        if (session == null) {
            session = TestUtils.newSession();
            // session.setConfigProperty("aether.transport.ipfs.multiaddr", "/ip4/127.0.0.1/tcp/5001");
            session.setConfigProperty("aether.transport.ipfs.multiaddr", "/ip4/127.0.0.1/tcp/" + kuboPort);
            session.setConfigProperty("aether.transport.ipfs.filesPrefix", "tmp");
            session.setConfigProperty("aether.transport.ipfs.refreshIpns", "false");
            session.setConfigProperty("aether.transport.ipfs.publishIpns", "false");
        }
        transporter = factory.newInstance(session, newRepo(url));
    }

    void setUp(UseCase useCase) {
        try {
            tempDir = TestFileUtils.createTempDir().toPath();
            Files.createDirectories(tempDir);

            newTransporter(useCase.remoteRepositoryUri);
            transporter.put(new PutTask(URI.create("file.txt")).setDataString("testtest"));
            transporter.put(new PutTask(URI.create("some%20space.txt")).setDataString("spacespace"));
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @AfterEach
    void tearDown() {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        factory = null;
        session = null;
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testClassify(UseCase useCase) {
        setUp(useCase);
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new ResourceNotFoundException("testtest")));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPeek(UseCase useCase) throws Exception {
        setUp(useCase);
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPeek_NotFound(UseCase useCase) throws Exception {
        setUp(useCase);
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPeek_Closed(UseCase useCase) throws Exception {
        setUp(useCase);
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_ToMemory(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("testtest", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(8L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_ToFile(UseCase useCase) throws Exception {
        setUp(useCase);
        Path file = tempDir.resolve("testGet_ToFile");
        Files.write(file, "whatever".getBytes(StandardCharsets.UTF_8));
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("file.txt")).setDataPath(file).setListener(listener);
        transporter.get(task);
        assertEquals("testtest", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        assertEquals(0L, listener.dataOffset);
        assertEquals(8L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals("testtest", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_EncodedResourcePath(UseCase useCase) throws Exception {
        setUp(useCase);
        GetTask task = new GetTask(URI.create("some%20space.txt"));
        transporter.get(task);
        assertEquals("spacespace", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_Fragment(UseCase useCase) throws Exception {
        setUp(useCase);
        GetTask task = new GetTask(URI.create("file.txt#ignored"));
        transporter.get(task);
        assertEquals("testtest", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_Query(UseCase useCase) throws Exception {
        setUp(useCase);
        GetTask task = new GetTask(URI.create("file.txt?ignored"));
        transporter.get(task);
        assertEquals("testtest", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_FileHandleLeak(UseCase useCase) throws Exception {
        setUp(useCase);
        transporter.put(new PutTask(URI.create("file.txt")).setDataString("content"));
        for (int i = 0; i < 100; i++) {
            Path file = tempDir.resolve("testGet_FileHandleLeak" + i);
            transporter.get(new GetTask(URI.create("file.txt")).setDataPath(file));
            assertTrue(Files.isRegularFile(file));
            assertTrue(Files.deleteIfExists(file), i + ", " + file.toAbsolutePath());
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_NotFound(UseCase useCase) throws Exception {
        setUp(useCase);
        try {
            transporter.get(new GetTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_Closed(UseCase useCase) throws Exception {
        setUp(useCase);
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_StartCancelled(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(8L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testGet_ProgressCancelled(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
        try {
            transporter.get(task);
            fail("Expected error");
        } catch (TransferCancelledException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
        assertEquals(0L, listener.dataOffset);
        assertEquals(8L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(1, listener.progressedCount);
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_FromMemory(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        // assertEquals("upload", new String(Files.readAllBytes(repoDir.resolve("file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_FromFile(UseCase useCase) throws Exception {
        setUp(useCase);
        Path file = tempDir.resolve("upload");
        Files.write(file, "upload".getBytes(StandardCharsets.UTF_8));
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataPath(file);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        // assertEquals("upload", new String(Files.readAllBytes(repoDir.resolve("file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_NonExistentParentDir(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("dir/sub/dir/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        // assertEquals(
        //        "upload",
        //        new String(Files.readAllBytes(repoDir.resolve("dir/sub/dir/file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_EncodedResourcePath(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("some%20space.txt"))
                .setListener(listener)
                .setDataString("OK");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(2L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        // assertEquals("OK", new String(Files.readAllBytes(repoDir.resolve("some space.txt")),
        // StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_Closed(UseCase useCase) throws Exception {
        setUp(useCase);
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_StartCancelled(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
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
        // assertFalse(Files.exists(repoDir.resolve("file.txt")));
    }

    @ParameterizedTest
    @EnumSource(UseCase.class)
    void testPut_ProgressCancelled(UseCase useCase) throws Exception {
        setUp(useCase);
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
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
        // assertFalse(Files.exists(repoDir.resolve("file.txt")));
    }

    @Test
    void testInit_BadProtocol() {
        assertThrows(NoTransporterException.class, () -> newTransporter("bad:/void"));
    }
}
