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
package org.eclipse.aether.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ChecksumUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testReadStandardChecksum() throws IOException {
        File file = tempFolder.newFile("test.sha1");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("  \n\r  1234567890abcdef1234567890abcdef12345678  test.jar\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("1234567890abcdef1234567890abcdef12345678", ChecksumUtils.read(file));
    }

    @Test
    public void testReadAlgorithmHeaderFormat() throws IOException {
        File file = tempFolder.newFile("test.sha1");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write("SHA1 (test.jar) = 1234567890abcdef1234567890abcdef12345678\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("1234567890abcdef1234567890abcdef12345678", ChecksumUtils.read(file));
    }

    @Test
    public void testReadBoundedLengthExceeded() throws IOException {
        File file = tempFolder.newFile("large.sha1");
        byte[] bytes = new byte[9000];
        Arrays.fill(bytes, (byte) 'a');
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        }
        try {
            ChecksumUtils.read(file);
            fail("Expected IOException due to exceeding 8192 characters");
        } catch (IOException e) {
            // expected
        }
    }
}
