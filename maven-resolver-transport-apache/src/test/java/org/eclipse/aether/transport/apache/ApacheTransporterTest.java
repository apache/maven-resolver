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

import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestPathProcessor;
import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.internal.test.util.http.RecordingTransportListener;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apache Transporter UT.
 * It does support WebDAV.
 */
class ApacheTransporterTest extends HttpTransporterTest {

    public ApacheTransporterTest() {
        super(() -> new ApacheTransporterFactory(standardChecksumExtractor(), new TestPathProcessor()));
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

        assertEquals(5, httpServer.getLogEntries().size());
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
        httpServer.addSslConnector();
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
                        ((ApacheTransporter) transporter).getState().getConnectionManager())
                .getTotalStats();
        assertEquals(0, stats.getAvailable(), stats.toString());
    }
}
