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

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public class FileTransporterTest {

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private File repoDir;

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("test", "default", url).build();
    }

    private void newTransporter(String url) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        transporter = factory.newInstance(session, newRepo(url));
    }

    @Before
    public void setUp() throws Exception {
        session = TestUtils.newSession();
        factory = new FileTransporterFactory();
        repoDir = TestFileUtils.createTempDir();
        TestFileUtils.writeString(new File(repoDir, "file.txt"), "test");
        TestFileUtils.writeString(new File(repoDir, "empty.txt"), "");
        TestFileUtils.writeString(new File(repoDir, "some space.txt"), "space");
        newTransporter(repoDir.toURI().toString());
    }

    @After
    public void tearDown() {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        factory = null;
        session = null;
    }

    @Test
    public void testClassify() {
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new FileNotFoundException()));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new ResourceNotFoundException("test")));
    }

    @Test
    public void testPeek() throws Exception {
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testPeekNotFound() throws Exception {
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testPeekClosed() throws Exception {
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGetToMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
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
        GetTask task = new GetTask(URI.create("file.txt")).setDataFile(file).setListener(listener);
        transporter.get(task);
        assertEquals("test", TestFileUtils.readString(file));
        assertEquals(0L, listener.dataOffset);
        assertEquals(4L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("test", new String(listener.baos.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void testGetEmptyResource() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask(URI.create("empty.txt")).setDataFile(file).setListener(listener);
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
        GetTask task = new GetTask(URI.create("some%20space.txt"));
        transporter.get(task);
        assertEquals("space", task.getDataString());
    }

    @Test
    public void testGetFragment() throws Exception {
        GetTask task = new GetTask(URI.create("file.txt#ignored"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
    }

    @Test
    public void testGetQuery() throws Exception {
        GetTask task = new GetTask(URI.create("file.txt?ignored"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
    }

    @Test
    public void testGetFileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File file = TestFileUtils.createTempFile("failure");
            transporter.get(new GetTask(URI.create("file.txt")).setDataFile(file));
            assertTrue(i + ", " + file.getAbsolutePath(), file.delete());
        }
    }

    @Test
    public void testGetNotFound() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceNotFoundException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testGetClosed() throws Exception {
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGetStartCancelled() throws Exception {
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

    @Test
    public void testGetProgressCancelled() throws Exception {
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

    @Test
    public void testPutFromMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
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
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataFile(file);
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
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    public void testPutNonExistentParentDir() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("dir/sub/dir/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "dir/sub/dir/file.txt")));
    }

    @Test
    public void testPutEncodedResourcePath() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("some%20space.txt"))
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
    public void testPutFileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File src = TestFileUtils.createTempFile("upload");
            File dst = new File(repoDir, "file.txt");
            transporter.put(new PutTask(URI.create("file.txt")).setDataFile(src));
            assertTrue(i + ", " + src.getAbsolutePath(), src.delete());
            assertTrue(i + ", " + dst.getAbsolutePath(), dst.delete());
        }
    }

    @Test
    public void testPutClosed() throws Exception {
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPutStartCancelled() throws Exception {
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
        assertFalse(new File(repoDir, "file.txt").exists());
    }

    @Test
    public void testPutProgressCancelled() throws Exception {
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
        assertFalse(new File(repoDir, "file.txt").exists());
    }

    @Test(expected = NoTransporterException.class)
    public void testInitBadProtocol() throws Exception {
        newTransporter("bad:/void");
    }

    @Test
    public void testInitCaseInsensitiveProtocol() throws Exception {
        newTransporter("file:/void");
        newTransporter("FILE:/void");
        newTransporter("File:/void");
    }

    @Test
    public void testInitOpaqueUrl() throws Exception {
        testInit("file:repository", "repository");
    }

    @Test
    public void testInitOpaqueUrlTrailingSlash() throws Exception {
        testInit("file:repository/", "repository");
    }

    @Test
    public void testInitOpaqueUrlSpaces() throws Exception {
        testInit("file:repo%20space", "repo space");
    }

    @Test
    public void testInitOpaqueUrlSpacesDecoded() throws Exception {
        testInit("file:repo space", "repo space");
    }

    @Test
    public void testInitHierarchicalUrl() throws Exception {
        testInit("file:/repository", "/repository");
    }

    @Test
    public void testInitHierarchicalUrlTrailingSlash() throws Exception {
        testInit("file:/repository/", "/repository");
    }

    @Test
    public void testInitHierarchicalUrlSpaces() throws Exception {
        testInit("file:/repo%20space", "/repo space");
    }

    @Test
    public void testInitHierarchicalUrlSpacesDecoded() throws Exception {
        testInit("file:/repo space", "/repo space");
    }

    @Test
    public void testInitHierarchicalUrlRoot() throws Exception {
        testInit("file:/", "/");
    }

    @Test
    public void testInitHierarchicalUrlHostNoPath() throws Exception {
        testInit("file://host/", "/");
    }

    @Test
    public void testInitHierarchicalUrlHostPath() throws Exception {
        testInit("file://host/dir", "/dir");
    }

    private void testInit(String base, String expected) throws Exception {
        newTransporter(base);
        File exp = new File(expected).getAbsoluteFile();
        assertEquals(exp, ((FileTransporter) transporter).getBasedir());
    }
}
