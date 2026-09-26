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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultChecksumProcessorTest {

    private static final String SHA1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    @TempDir
    private Path targetDir;

    private DefaultChecksumProcessor checksumProcessor;

    @BeforeEach
    void setup() {
        checksumProcessor = new DefaultChecksumProcessor(new DefaultPathProcessor());
    }

    private Path write(String content) throws IOException {
        Path file = targetDir.resolve("checksum.sha1");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void testReadChecksum() throws IOException {
        assertEquals(SHA1, checksumProcessor.readChecksum(write(SHA1)));
    }

    @Test
    void testReadChecksumEmptyFile() throws IOException {
        assertEquals("", checksumProcessor.readChecksum(write("")));
    }

    @Test
    void testReadChecksumLeadingEmptyLinesAndWhitespace() throws IOException {
        assertEquals(SHA1, checksumProcessor.readChecksum(write("\n\r\n  \n " + SHA1 + " \n")));
    }

    @Test
    void testReadChecksumAlgorithmHeaderFormat() throws IOException {
        assertEquals(SHA1, checksumProcessor.readChecksum(write("foobar(alg) = " + SHA1)));
    }

    @Test
    void testReadChecksumFirstTokenFormat() throws IOException {
        assertEquals(SHA1, checksumProcessor.readChecksum(write(SHA1 + " foobar")));
    }

    @Test
    void testReadChecksumNonHexTailFallsBackToFirstToken() throws IOException {
        // "= " present but tail is not hex: not the "<algorithm> (<file>) = <hex>" format,
        // so the first token wins (matches the historic regex-based behavior)
        assertEquals("foo=", checksumProcessor.readChecksum(write("foo= xyz")));
    }

    @Test
    void testReadChecksumOversizedInputRejected() throws IOException {
        char[] big = new char[DefaultChecksumProcessor.MAX_CHECKSUM_FILE_CHARS + 1];
        Arrays.fill(big, 'a');
        Path file = write(new String(big));
        IOException e = assertThrows(IOException.class, () -> checksumProcessor.readChecksum(file));
        assertTrue(e.getMessage().contains("malformed"));
    }

    @Test
    void testReadChecksumOversizedSecondLineRejected() throws IOException {
        // the bound is on total data consumed, not per line: a checksum hidden behind a huge
        // preamble of blank lines is equally malformed
        char[] blanks = new char[DefaultChecksumProcessor.MAX_CHECKSUM_FILE_CHARS + 1];
        Arrays.fill(blanks, '\n');
        Path file = write(new String(blanks) + SHA1);
        assertThrows(IOException.class, () -> checksumProcessor.readChecksum(file));
    }

    @Test
    void testReadChecksumWithinBoundAccepted() throws IOException {
        // a legal checksum preceded by a modest preamble stays accepted
        assertEquals(SHA1, checksumProcessor.readChecksum(write("\n\n\n" + SHA1)));
    }
}
