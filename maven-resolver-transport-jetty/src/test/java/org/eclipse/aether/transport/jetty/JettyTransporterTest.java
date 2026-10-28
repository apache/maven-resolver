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
package org.eclipse.aether.transport.jetty;

import java.io.File;
import java.net.URI;
import java.util.stream.Stream;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.http.HttpTransporterTest;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessorSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Jetty transporter UT.
 */
class JettyTransporterTest extends HttpTransporterTest {

    @Override
    protected Stream<String> supportedCompressionAlgorithms() {
        return Stream.of("gzip", "deflate", "br", "zstd");
    }

    @Override
    @Disabled
    @Test
    protected void testAuthSchemeReuse() {}

    @Override
    @Disabled
    @Test
    protected void testPut_ProxyUnauthenticated() {}

    @Override
    @Disabled
    @Test
    protected void testPut_Unauthenticated() {}

    @Override
    @Disabled
    @Test
    protected void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader() {}

    @Override
    @Disabled
    @Test
    protected void testGet_HTTP3FallbackToHTTP2() throws Exception {
        // Jetty does not support version discovery: https://github.com/jetty/jetty.project/issues/15423
    }

    @Override
    @Test
    protected void testGet_HTTP3Only() throws Exception {
        // Jetty's HTTP/3 support is based on Quiche which does not consider the default SSL context
        // (https://github.com/jetty/jetty.project/issues/15370)
        session.setConfigProperty(
                ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
        super.testGet_HTTP3Only();
    }

    @Override
    @Test
    protected void testGet_HTTP3() throws Exception {
        // Jetty's HTTP/3 support is based on Quiche which does not consider the default SSL context
        // (https://github.com/jetty/jetty.project/issues/15370)
        session.setConfigProperty(
                ConfigurationProperties.HTTPS_SECURITY_MODE, ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE);
        super.testGet_HTTP3();
    }

    // the retry tests of the base class only cover HEAD requests

    private void configureQuickRetries(int retryCount) throws Exception {
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT, retryCount);
        session.setConfigProperty(ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL, 1);
        newTransporter(httpServer.getHttpUrl());
    }

    @Test
    void testGet_RetriesAfterServiceUnavailable() throws Exception {
        configureQuickRetries(2);
        httpServer.setServerErrorsBeforeWorks(2, 503);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
        assertEquals(3, httpServer.getLogEntries().size());
    }

    @Test
    void testGet_RetriesAfterClosedConnection() throws Exception {
        configureQuickRetries(2);
        httpServer.setConnectionsToClose(2);
        GetTask task = new GetTask(URI.create("repo/file.txt"));
        transporter.get(task);
        assertEquals("test", task.getDataString());
    }

    @Test
    void testGet_GivesUpAfterRetryCount() throws Exception {
        configureQuickRetries(2);
        httpServer.setServerErrorsBeforeWorks(3, 503);
        HttpTransporterException e = assertThrows(
                HttpTransporterException.class, () -> transporter.get(new GetTask(URI.create("repo/file.txt"))));
        assertEquals(503, e.getStatusCode());
        assertEquals(3, httpServer.getLogEntries().size());
    }

    @Test
    void testPut_RetriesAfterServiceUnavailable() throws Exception {
        configureQuickRetries(2);
        httpServer.setServerErrorsBeforeWorks(2, 503);
        transporter.put(new PutTask(URI.create("repo/file.txt")).setDataString("upload"));
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
        assertEquals(3, httpServer.getLogEntries().size());
    }

    @Test
    void testPut_RetriesAfterClosedConnection() throws Exception {
        configureQuickRetries(2);
        httpServer.setConnectionsToClose(2);
        transporter.put(new PutTask(URI.create("repo/file.txt")).setDataString("upload"));
        assertEquals("upload", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    @Test
    void testPut_GivesUpAfterRetryCount() throws Exception {
        configureQuickRetries(1);
        httpServer.setServerErrorsBeforeWorks(2, 503);
        HttpTransporterException e = assertThrows(
                HttpTransporterException.class,
                () -> transporter.put(new PutTask(URI.create("repo/file.txt")).setDataString("upload")));
        assertEquals(503, e.getStatusCode());
        assertEquals(2, httpServer.getLogEntries().size());
        assertEquals("test", TestFileUtils.readString(new File(repoDir, "file.txt")));
    }

    public JettyTransporterTest() {
        super(() -> new JettyTransporterFactory(standardChecksumExtractor(), new PathProcessorSupport()));
    }
}
