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

import org.junit.jupiter.api.Test;

import static org.eclipse.aether.util.StringDigestUtil.fromHexString;
import static org.eclipse.aether.util.StringDigestUtil.toHexString;
import static org.junit.jupiter.api.Assertions.*;

public class StringDigestUtilTest {
    @Test
    void sha1Simple() {
        assertEquals(StringDigestUtil.sha1(null), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(StringDigestUtil.sha1(""), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(StringDigestUtil.sha1("something"), "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
        assertEquals(StringDigestUtil.sha1().update(null).digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(StringDigestUtil.sha1().update("").digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(StringDigestUtil.sha1().update("something").digest(), "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
        assertEquals(
                StringDigestUtil.sha1().update("some").update("thing").digest(),
                "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
    }

    @Test
    void sha1Manual() {
        assertEquals(new StringDigestUtil("SHA-1").digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(new StringDigestUtil("SHA-1").update("").digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(
                new StringDigestUtil("SHA-1").update("something").digest(), "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
        assertEquals(new StringDigestUtil("SHA-1").update(null).digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(new StringDigestUtil("SHA-1").update("").digest(), "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertEquals(
                new StringDigestUtil("SHA-1").update("something").digest(), "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
        assertEquals(
                new StringDigestUtil("SHA-1").update("some").update("thing").digest(),
                "1af17e73721dbe0c40011b82ed4bb1a7dbe3ce29");
    }

    @Test
    void md5Manual() {
        assertEquals(new StringDigestUtil("MD5").digest(), "d41d8cd98f00b204e9800998ecf8427e");
        assertEquals(new StringDigestUtil("MD5").update("").digest(), "d41d8cd98f00b204e9800998ecf8427e");
        assertEquals(new StringDigestUtil("MD5").update("something").digest(), "437b930db84b8079c2dd804a71936b5f");
        assertEquals(new StringDigestUtil("MD5").update(null).digest(), "d41d8cd98f00b204e9800998ecf8427e");
        assertEquals(new StringDigestUtil("MD5").update("").digest(), "d41d8cd98f00b204e9800998ecf8427e");
        assertEquals(new StringDigestUtil("MD5").update("something").digest(), "437b930db84b8079c2dd804a71936b5f");
        assertEquals(
                new StringDigestUtil("MD5").update("some").update("thing").digest(),
                "437b930db84b8079c2dd804a71936b5f");
    }

    @Test
    void unsupportedAlg() {
        try {
            new StringDigestUtil("FOO-BAR");
            fail("StringDigestUtil should throw");
        } catch (IllegalStateException e) {
            // good
        }
    }

    @Test
    void testToHexString() {
        assertNull(toHexString(null));
        assertEquals("", toHexString(new byte[] {}));
        assertEquals("00", toHexString(new byte[] {0}));
        assertEquals("ff", toHexString(new byte[] {-1}));
        assertEquals("00017f", toHexString(new byte[] {0, 1, 127}));
    }

    @Test
    void testFromHexString() {
        assertNull(fromHexString(null));
        assertArrayEquals(new byte[] {}, fromHexString(""));
        assertArrayEquals(new byte[] {0}, fromHexString("00"));
        assertArrayEquals(new byte[] {-1}, fromHexString("ff"));
        assertArrayEquals(new byte[] {0, 1, 127}, fromHexString("00017f"));
    }
}
