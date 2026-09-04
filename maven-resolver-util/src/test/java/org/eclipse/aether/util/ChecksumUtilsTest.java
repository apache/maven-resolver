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
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.eclipse.aether.internal.test.util.TestFileUtils.createTempFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ChecksumUtilsTest {

    @Test
    public void testReadStandardChecksum() throws IOException {
        File file = createTempFile("  \n\r  1234567890abcdef1234567890abcdef12345678  test.jar\n");
        assertEquals("1234567890abcdef1234567890abcdef12345678", ChecksumUtils.read(file));
    }

    @Test
    public void testReadAlgorithmHeaderFormat() throws IOException {
        File file = createTempFile("SHA1 (test.jar) = 1234567890abcdef1234567890abcdef12345678\n");
        assertEquals("1234567890abcdef1234567890abcdef12345678", ChecksumUtils.read(file));
    }

    @Test
    public void testReadBoundedLengthExceeded() throws IOException {
        File file = createTempFile("a".getBytes(StandardCharsets.UTF_8), 9000);
        try {
            ChecksumUtils.read(file);
            fail("Expected IOException due to exceeding 8192 characters");
        } catch (IOException e) {
            // expected
        }
    }
}
