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
package org.eclipse.aether.transport.wagon;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.eclipse.aether.ConfigurationProperties;
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
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public abstract class AbstractWagonTransporterTest {

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private String id;

    private Map<String, String> fs;

    protected abstract Wagon newWagon();

    private RemoteRepository newRepo(String url) {
        return new RemoteRepository.Builder("test", "default", url).build();
    }

    private void newTransporter(String url) throws Exception {
        newTransporter(newRepo(url));
    }

    private void newTransporter(RemoteRepository repo) throws Exception {
        if (transporter != null) {
            transporter.close();
            transporter = null;
        }
        transporter = factory.newInstance(session, repo);
    }

    @Before
    public void setUp() throws Exception {
        session = TestUtils.newSession();
        factory = new WagonTransporterFactory(
                new WagonProvider() {
                    public Wagon lookup(String roleHint) {
                        if ("mem".equalsIgnoreCase(roleHint)) {
                            return newWagon();
                        }
                        throw new IllegalArgumentException("unknown wagon role: " + roleHint);
                    }

                    public void release(Wagon wagon) {}
                },
                new WagonConfigurator() {
                    public void configure(Wagon wagon, Object configuration) {
                        ((Configurable) wagon).setConfiguration(configuration);
                    }
                });
        id = UUID.randomUUID().toString().replace("-", "");
        fs = MemWagonUtils.getFilesystem(id);
        fs.put("file.txt", "test");
        fs.put("empty.txt", "");
        fs.put("some space.txt", "space");
        newTransporter("mem://" + id);
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
        assertEquals(Transporter.ERROR_OTHER, transporter.classify(new TransferFailedException("test")));
        assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(new ResourceDoesNotExistException("test")));
    }

    @Test
    public void testPeek() throws Exception {
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testPeek_NotFound() throws Exception {
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceDoesNotExistException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testPeek_Closed() throws Exception {
        transporter.close();
        try {
            transporter.peek(new PeekTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGet_ToMemory() throws Exception {
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
    public void testGet_ToFile() throws Exception {
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
    public void testGet_EmptyResource() throws Exception {
        File file = TestFileUtils.createTempFile("failure");
        assertTrue(file.delete() && !file.exists());
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
    public void testGet_EncodedResourcePath() throws Exception {
        GetTask task = new GetTask(URI.create("some%20space.txt"));
        transporter.get(task);
        assertEquals("space", task.getDataString());
    }

    @Test
    public void testGet_FileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File file = TestFileUtils.createTempFile("failure");
            transporter.get(new GetTask(URI.create("file.txt")).setDataFile(file));
            assertTrue(i + ", " + file.getAbsolutePath(), file.delete());
        }
    }

    @Test
    public void testGet_NotFound() throws Exception {
        try {
            transporter.get(new GetTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (ResourceDoesNotExistException e) {
            assertEquals(Transporter.ERROR_NOT_FOUND, transporter.classify(e));
        }
    }

    @Test
    public void testGet_Closed() throws Exception {
        transporter.close();
        try {
            transporter.get(new GetTask(URI.create("file.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testGet_StartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        GetTask task = new GetTask(URI.create("file.txt")).setListener(listener);
        transporter.get(task);
        assertEquals(1, listener.startedCount);
    }

    @Test
    public void testGet_ProgressCancelled() throws Exception {
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
    public void testPut_FromMemory() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", fs.get("file.txt"));
    }

    @Test
    public void testPut_FromFile() throws Exception {
        File file = TestFileUtils.createTempFile("upload");
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataFile(file);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", fs.get("file.txt"));
    }

    @Test
    public void testPut_EmptyResource() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener);
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(0L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertEquals(0, listener.progressedCount);
        assertEquals("", fs.get("file.txt"));
    }

    @Test
    public void testPut_NonExistentParentDir() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("dir/sub/dir/file.txt"))
                .setListener(listener)
                .setDataString("upload");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(6L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("upload", fs.get("dir/sub/dir/file.txt"));
    }

    @Test
    public void testPut_EncodedResourcePath() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask(URI.create("some%20space.txt"))
                .setListener(listener)
                .setDataString("OK");
        transporter.put(task);
        assertEquals(0L, listener.dataOffset);
        assertEquals(2L, listener.dataLength);
        assertEquals(1, listener.startedCount);
        assertTrue("Count: " + listener.progressedCount, listener.progressedCount > 0);
        assertEquals("OK", fs.get("some space.txt"));
    }

    @Test
    public void testPut_FileHandleLeak() throws Exception {
        for (int i = 0; i < 100; i++) {
            File src = TestFileUtils.createTempFile("upload");
            transporter.put(new PutTask(URI.create("file.txt")).setDataFile(src));
            assertTrue(i + ", " + src.getAbsolutePath(), src.delete());
        }
    }

    @Test
    public void testPut_Closed() throws Exception {
        transporter.close();
        try {
            transporter.put(new PutTask(URI.create("missing.txt")));
            fail("Expected error");
        } catch (IllegalStateException e) {
            assertEquals(Transporter.ERROR_OTHER, transporter.classify(e));
        }
    }

    @Test
    public void testPut_StartCancelled() throws Exception {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        PutTask task = new PutTask(URI.create("file.txt")).setListener(listener).setDataString("upload");
        transporter.put(task);
        assertEquals(1, listener.startedCount);
    }

    @Test
    public void testPut_ProgressCancelled() throws Exception {
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
    }

    @Test(expected = NoTransporterException.class)
    public void testInit_BadProtocol() throws Exception {
        newTransporter("bad:/void");
    }

    @Test
    public void testInit_CaseInsensitiveProtocol() throws Exception {
        newTransporter("mem:/void");
        newTransporter("MEM:/void");
        newTransporter("mEm:/void");
    }

    @Test
    public void testInit_Configuration() throws Exception {
        session.setConfigProperty("aether.connector.wagon.config.test", "passed");
        newTransporter("mem://" + id + "?config=passed");
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testInit_UserAgent() throws Exception {
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, "Test/1.0");
        newTransporter("mem://" + id + "?userAgent=Test/1.0");
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testInit_Timeout() throws Exception {
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, "12345678");
        newTransporter("mem://" + id + "?requestTimeout=12345678");
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testInit_ServerAuth() throws Exception {
        String url = "mem://" + id + "?serverUsername=testuser&serverPassword=testpass"
                + "&serverPrivateKey=testkey&serverPassphrase=testphrase";
        Authentication auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .addPrivateKey("testkey", "testphrase")
                .build();
        RemoteRepository repo = new RemoteRepository.Builder("test", "default", url)
                .setAuthentication(auth)
                .build();
        newTransporter(repo);
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testInit_Proxy() throws Exception {
        String url = "mem://" + id + "?proxyHost=testhost&proxyPort=8888";
        RemoteRepository repo = new RemoteRepository.Builder("test", "default", url)
                .setProxy(new Proxy("http", "testhost", 8888))
                .build();
        newTransporter(repo);
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }

    @Test
    public void testInit_ProxyAuth() throws Exception {
        String url = "mem://" + id + "?proxyUsername=testuser&proxyPassword=testpass";
        Authentication auth = new AuthenticationBuilder()
                .addUsername("testuser")
                .addPassword("testpass")
                .build();
        RemoteRepository repo = new RemoteRepository.Builder("test", "default", url)
                .setProxy(new Proxy("http", "testhost", 8888, auth))
                .build();
        newTransporter(repo);
        transporter.peek(new PeekTask(URI.create("file.txt")));
    }
}
