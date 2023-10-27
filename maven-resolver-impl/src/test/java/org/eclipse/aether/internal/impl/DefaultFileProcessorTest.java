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
package org.eclipse.aether.internal.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.spi.io.FileProcessor.ProgressListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class DefaultFileProcessorTest {

    private File targetDir;

    private DefaultFileProcessor fileProcessor;

    @BeforeEach
    void setup() throws IOException {
        targetDir = TestFileUtils.createTempDir(getClass().getSimpleName());
        fileProcessor = new DefaultFileProcessor();
    }

    @AfterEach
    void teardown() throws Exception {
        TestFileUtils.deleteFile(targetDir);
        fileProcessor = null;
    }

    @Test
    void testCopy() throws IOException {
        String data = "testCopy\nasdf";
        File file = TestFileUtils.createTempFile(data);
        File target = new File(targetDir, "testCopy.txt");

        fileProcessor.copy(file, target);

        assertEquals(data, TestFileUtils.readString(file));

        file.delete();
    }

    @Test
    void testOverwrite() throws IOException {
        String data = "testCopy\nasdf";
        File file = TestFileUtils.createTempFile(data);

        for (int i = 0; i < 5; i++) {
            File target = new File(targetDir, "testCopy.txt");
            fileProcessor.copy(file, target);
            assertEquals(data, TestFileUtils.readString(file));
        }

        file.delete();
    }

    @Test
    void testCopyEmptyFile() throws IOException {
        File file = TestFileUtils.createTempFile("");
        File target = new File(targetDir, "testCopyEmptyFile");
        target.delete();
        fileProcessor.copy(file, target);
        assertTrue(target.exists() && target.length() == 0L, "empty file was not copied");
        target.delete();
    }

    @Test
    void testProgressingChannel() throws IOException {
        File file = TestFileUtils.createTempFile("test");
        File target = new File(targetDir, "testProgressingChannel");
        target.delete();
        final AtomicInteger progressed = new AtomicInteger();
        ProgressListener listener = new ProgressListener() {
            public void progressed(ByteBuffer buffer) {
                progressed.addAndGet(buffer.remaining());
            }
        };
        fileProcessor.copy(file, target, listener);
        assertTrue(target.isFile(), "file was not created");
        assertEquals(4L, target.length(), "file was not fully copied");
        assertEquals(4, progressed.intValue(), "listener not called");
        target.delete();
    }

    @Test
    void testWrite() throws IOException {
        String data = "testCopy\nasdf";
        File target = new File(targetDir, "testWrite.txt");

        fileProcessor.write(target, data);

        assertEquals(data, TestFileUtils.readString(target));

        target.delete();
    }

    /**
     * Used ONLY when FileProcessor present, never otherwise.
     */
    @Test
    void testWriteStream() throws IOException {
        String data = "testCopy\nasdf";
        File target = new File(targetDir, "testWriteStream.txt");

        fileProcessor.write(target, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));

        assertEquals(data, TestFileUtils.readString(target));

        target.delete();
    }

    @Test
    void testReadChecksumEmptyFile() throws IOException {
        File emptyFile = TestFileUtils.createTempFile("");
        String read = fileProcessor.readChecksum(emptyFile);
        assertEquals("", read);
    }

    @Test
    void testReadChecksum() throws IOException {
        String checksum = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
        File checksumFile = TestFileUtils.createTempFile(checksum);
        String read = fileProcessor.readChecksum(checksumFile);
        assertEquals(checksum, read);
    }

    @Test
    void testReadChecksumWhitespace() throws IOException {
        String checksum = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
        File checksumFile;
        String read;

        checksumFile = TestFileUtils.createTempFile("foobar(alg) = " + checksum);
        read = fileProcessor.readChecksum(checksumFile);
        assertEquals(checksum, read);

        checksumFile = TestFileUtils.createTempFile(checksum + " foobar");
        read = fileProcessor.readChecksum(checksumFile);
        assertEquals(checksum, read);
    }
}
