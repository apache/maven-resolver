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
package org.eclipse.aether.transport.file;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class FileTransporterTest {

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private Path repoDir;

    private FileSystem fileSystem;

    enum FS {
        DEFAULT,
        JIMFS
    }

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("test", "default", url).build();
    }

    private void newTransporter(String url) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (factory == null) {
            factory = new FileTransporterFactory();
        }
        if (session == null) {
            session = TestUtils.newSession();
        }
        transporter = factory.newInstance(session, newRepo(url));
    }

    void setUp(FS fs) {
        try {
            fileSystem = fs == FS.JIMFS ? Jimfs.newFileSystem() : null;
            repoDir = fileSystem == null ? TestFileUtils.createTempDir().toPath() : fileSystem.getPath(".");
            Files.write(repoDir.resolve("file.txt"), "test".getBytes(StandardCharsets.UTF_8));
            Files.write(repoDir.resolve("empty.txt"), "".getBytes(StandardCharsets.UTF_8));
            Files.write(repoDir.resolve("some space.txt"), "space".getBytes(StandardCharsets.UTF_8));
            newTransporter(repoDir.toUri().toASCIIString());
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        if (fileSystem != null) {
            fileSystem.close();
        }
        factory = null;
        session = null;
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testClassify(FS fs) {
        setUp(fs);
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new ResourceNotFoundException("test")));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPeek(FS fs) throws Exception {
        setUp(fs);
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPeek_NotFound(FS fs) throws Exception {
        setUp(fs);
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPeek_Closed(FS fs) throws Exception {
        setUp(fs);
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_ToMemory(FS fs) throws Exception {
        setUp(fs);
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals(task.getDataString(), new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_ToFile(FS fs) throws Exception {
        setUp(fs);
        Path file = TestFileUtils.createTempFile("failure").toPath();
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("file.txt")).setDataPath(file).setListener(listener);
        transporter.get(task);
        assertEquals("test", TestFileUtils.readString(file.toFile()));
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals("test", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_EmptyResource(FS fs) throws Exception {
        setUp(fs);
        Path file = TestFileUtils.createTempFile("failure").toPath();
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("empty.txt")).setDataPath(file).setListener(listener);
        transporter.get(task);
        assertEquals("", TestFileUtils.readString(file.toFile()));
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_EncodedResourcePath(FS fs) throws Exception {
        setUp(fs);
        GetTask task = new GetTask(URI.create("some%20space.txt"));
        transporter.get(task);
        assertEquals("space", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_Fragment(FS fs) throws Exception {
        setUp(fs);
        GetTask task = new GetTask(URI.create("file.txt#ignored"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_Query(FS fs) throws Exception {
        setUp(fs);
        GetTask task = new GetTask(URI.create("file.txt?ignored"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_FileHandleLeak(FS fs) throws Exception {
        setUp(fs);
        for (int i = 0; i < 100; i++) {
            Path file = TestFileUtils.createTempFile("failure").toPath();
            transporter.get(new GetTask(URI.create("file.txt")).setDataPath(file));
            assertTrue(file.toFile().delete(), i + ", " + file.toFile().getAbsolutePath());
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_NotFound(FS fs) throws Exception {
        setUp(fs);
        try {
            transporter.get(new GetTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_Closed(FS fs) throws Exception {
        setUp(fs);
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_StartCancelled(FS fs) throws Exception {
        setUp(fs);
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
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testGet_ProgressCancelled(FS fs) throws Exception {
        setUp(fs);
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
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(1, listener.progressedCount);
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_FromMemory(FS fs) throws Exception {
        setUp(fs);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals("upload", new String(Files.readAllBytes(repoDir.resolve("file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_FromFile(FS fs) throws Exception {
        setUp(fs);
        Path file = TestFileUtils.createTempFile("upload").toPath();
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataPath(file);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals("upload", new String(Files.readAllBytes(repoDir.resolve("file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_EmptyResource(FS fs) throws Exception {
        setUp(fs);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", new String(Files.readAllBytes(repoDir.resolve("file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_NonExistentParentDir(FS fs) throws Exception {
        setUp(fs);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("dir/sub/dir/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals(
                "upload",
                new String(Files.readAllBytes(repoDir.resolve("dir/sub/dir/file.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_EncodedResourcePath(FS fs) throws Exception {
        setUp(fs);
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("some%20space.txt"))
                .setListener(listener)
                .setDataString("OK");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(2L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue(listener.progressedCount > 0, "Count: " + listener.progressedCount);
        assertEquals("OK", new String(Files.readAllBytes(repoDir.resolve("some space.txt")), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_FileHandleLeak(FS fs) throws Exception {
        setUp(fs);
        for (int i = 0; i < 100; i++) {
            Path src = TestFileUtils.createTempFile("upload").toPath();
            Path dst = repoDir.resolve("file.txt");
            transporter.put(new PutTask(URI.create("file.txt")).setDataPath(src));
            assertTrue(src.toFile().delete(), i + ", " + src.toFile().getAbsolutePath());
            assertTrue(Files.deleteIfExists(dst), i + ", " + dst.toAbsolutePath());
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_Closed(FS fs) throws Exception {
        setUp(fs);
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_StartCancelled(FS fs) throws Exception {
        setUp(fs);
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
        assertFalse(Files.exists(repoDir.resolve("file.txt")));
    }

    @ParameterizedTest
    @EnumSource(FS.class)
    void testPut_ProgressCancelled(FS fs) throws Exception {
        setUp(fs);
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
        assertFalse(Files.exists(repoDir.resolve("file.txt")));
    }

    @Test
    void testInit_BadProtocol() {
        assertThrows(NoTransporterException.class, () -> newTransporter("bad:/void"));
    }

    @Test
    void testInit_CaseInsensitiveProtocol() throws Exception {
        newTransporter("file:/void");
        newTransporter("FILE:/void");
        newTransporter("File:/void");
    }

    @Test
    void testInit_OpaqueUrl() throws Exception {
        testInit("file:repository", "repository");
    }

    @Test
    void testInit_OpaqueUrlTrailingSlash() throws Exception {
        testInit("file:repository/", "repository");
    }

    @Test
    void testInit_OpaqueUrlSpaces() throws Exception {
        testInit("file:repo%20space", "repo space");
    }

    @Test
    void testInit_OpaqueUrlSpacesDecoded() throws Exception {
        testInit("file:repo space", "repo space");
    }

    @Test
    void testInit_HierarchicalUrl() throws Exception {
        testInit("file:/repository", "/repository");
    }

    @Test
    void testInit_HierarchicalUrlTrailingSlash() throws Exception {
        testInit("file:/repository/", "/repository");
    }

    @Test
    void testInit_HierarchicalUrlSpaces() throws Exception {
        testInit("file:/repo%20space", "/repo space");
    }

    @Test
    void testInit_HierarchicalUrlSpacesDecoded() throws Exception {
        testInit("file:/repo space", "/repo space");
    }

    @Test
    void testInit_HierarchicalUrlRoot() throws Exception {
        testInit("file:/", "/");
    }

    @Test
    void testInit_HierarchicalUrlHostNoPath() throws Exception {
        testInit("file://host/", "/");
    }

    @Test
    void testInit_HierarchicalUrlHostPath() throws Exception {
        testInit("file://host/dir", "/dir");
    }

    @Test
    void testInit_NonDefaultFileSystemRelative() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path path = fs.getPath("dir");
            testInit(path.toUri().toASCIIString(), "/work/dir");
        }
    }

    @Test
    void testInit_NonDefaultFileSystemAbsolute() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path path = fs.getPath("/dir");
            testInit(path.toUri().toASCIIString(), "/dir");
        }
    }

    private void testInit(String base, String expected) throws Exception {
        newTransporter(base);
        String exp = expected;
        if (base.startsWith("file:")) {
            // on def FileSystem we do extra dance that we do NOT do in case of non-default File Systems:
            // like accepting weird URLs/URIs and resolving/abs against CWD, that may not be defined in case
            // of non-default FileSystems (OTOH, they MAY do it, like JIMFS has $cwd="/work")
            exp = Paths.get(expected).toAbsolutePath().toString();
        }
        // compare path string representation only, as otherwise (Object equality) it would fail
        // if we end up with non default FS for example
        assertEquals(exp, ((FileTransporter) transporter).getBasePath().toString());
    }
}
