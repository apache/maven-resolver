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
package org.eclipse.aether.spi.connector.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractTransporterTest {

    private static final byte[] DATA = "data".getBytes(StandardCharsets.UTF_8);
    private static final TestTransporter TRANSPORTER = new TestTransporter();

    @Test
    void testUtilGetClosesInputStreamWhenRequested() throws Exception {
        CloseAwareInputStream input = new CloseAwareInputStream(DATA);
        GetTask task = new GetTask(URI.create("file.txt"));

        TRANSPORTER.utilGet(task, input, true, DATA.length, false);

        assertArrayEquals(DATA, task.getDataBytes());
        assertTrue(input.isClosed());
    }

    @Test
    void testUtilGetLeavesInputStreamOpenWhenRequested() throws Exception {
        CloseAwareInputStream input = new CloseAwareInputStream(DATA);
        GetTask task = new GetTask(URI.create("file.txt"));

        TRANSPORTER.utilGet(task, input, false, DATA.length, false);

        assertArrayEquals(DATA, task.getDataBytes());
        assertFalse(input.isClosed());
    }

    @Test
    void testUtilPutClosesOutputStreamWhenRequested() throws Exception {
        CloseAwareOutputStream output = new CloseAwareOutputStream();
        PutTask task = new PutTask(URI.create("file.txt")).setDataBytes(DATA);

        TRANSPORTER.utilPut(task, output, true);

        assertArrayEquals(DATA, output.toByteArray());
        assertTrue(output.isClosed());
        assertFalse(output.isFlushed());
    }

    @Test
    void testUtilPutFlushesOutputStreamWhenLeftOpen() throws Exception {
        CloseAwareOutputStream output = new CloseAwareOutputStream();
        PutTask task = new PutTask(URI.create("file.txt")).setDataBytes(DATA);

        TRANSPORTER.utilPut(task, output, false);

        assertArrayEquals(DATA, output.toByteArray());
        assertFalse(output.isClosed());
        assertTrue(output.isFlushed());
    }

    private static final class TestTransporter extends AbstractTransporter {
        @Override
        public int classify(Throwable error) {
            return ERROR_OTHER;
        }

        @Override
        protected void implPeek(PeekTask task) {}

        @Override
        protected void implGet(GetTask task) {}

        @Override
        protected void implPut(PutTask task) {}

        @Override
        protected void implClose() {}
    }

    private static final class CloseAwareInputStream extends ByteArrayInputStream {
        private boolean closed;

        CloseAwareInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static final class CloseAwareOutputStream extends ByteArrayOutputStream {
        private boolean closed;
        private boolean flushed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }

        boolean isClosed() {
            return closed;
        }

        boolean isFlushed() {
            return flushed;
        }
    }
}
