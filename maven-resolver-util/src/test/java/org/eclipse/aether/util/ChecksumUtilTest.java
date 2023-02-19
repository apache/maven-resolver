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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.eclipse.aether.internal.test.util.TestFileUtils.*;
import static org.junit.Assert.*;

public class ChecksumUtilTest {
    private static final String EMPTY = "EMPTY";
    private static final String PATTERN = "PATTERN";
    private static final String TEXT = "TEXT";

    private Map<String, File> files = new HashMap<>(3);

    private Map<String, byte[]> bytes = new HashMap<>(3);

    private static Map<String, String> emptyChecksums = new HashMap<>();

    private static Map<String, String> patternChecksums = new HashMap<>();

    private static Map<String, String> textChecksums = new HashMap<>();

    private Map<String, Map<String, String>> sums = new HashMap<>();

    @BeforeClass
    public static void beforeClass() {
        emptyChecksums.put("MD5", "d41d8cd98f00b204e9800998ecf8427e");
        emptyChecksums.put("SHA-1", "da39a3ee5e6b4b0d3255bfef95601890afd80709");
        emptyChecksums.put("SHA-256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        emptyChecksums.put(
                "SHA-512",
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
        patternChecksums.put("MD5", "14f01d6c7de7d4cf0a4887baa3528b5a");
        patternChecksums.put("SHA-1", "feeeda19f626f9b0ef6cbf5948c1ec9531694295");
        patternChecksums.put("SHA-256", "81d480a714840ab206dc8de62ca6119036f65499ad9e2e227c2465551bed684d");
        patternChecksums.put(
                "SHA-512",
                "931aa34118d9a85b9514e0224046d736a5bd7e2b2f366505fe1ad07ed85e1a4ac0cbc18e9b9a7fe36ce835be2a18cb571202a4975d182553faff336970eb0b7e");
        textChecksums.put("MD5", "12582d1a662cefe3385f2113998e43ed");
        textChecksums.put("SHA-1", "a8ae272db549850eef2ff54376f8cac2770745ee");
        textChecksums.put("SHA-256", "35829adced2979761ba521dc2bb7d166e92ebed7342319d041398e509d481a46");
        textChecksums.put(
                "SHA-512",
                "2d6d19570b26080fa88101af2256ce3dae63512b06864cd36a05371c81d6dbd0ec226dd75f22e8d46a9582e1fc40ee6e7a02d43c852f3c92255982b835db6e7c");
    }

    @Before
    public void before() throws IOException {
        sums.clear();

        byte[] emptyBytes = new byte[0];
        bytes.put(EMPTY, emptyBytes);
        files.put(EMPTY, createTempFile(emptyBytes, 0));
        sums.put(EMPTY, emptyChecksums);

        byte[] patternBytes =
                writeBytes(new byte[] {0, 1, 2, 4, 8, 16, 32, 64, 127, -1, -2, -4, -8, -16, -32, -64, -127}, 1000);
        bytes.put(PATTERN, patternBytes);
        files.put(PATTERN, createTempFile(patternBytes, 1));
        sums.put(PATTERN, patternChecksums);

        byte[] textBytes =
                writeBytes("the quick brown fox jumps over the lazy dog\n".getBytes(StandardCharsets.UTF_8), 500);
        bytes.put(TEXT, textBytes);
        files.put(TEXT, createTempFile(textBytes, 1));
        sums.put(TEXT, textChecksums);
    }

    @Test
    public void testEquality() throws Throwable {
        Map<String, Object> checksums = null;

        for (Map.Entry<String, File> fileEntry : files.entrySet()) {

            checksums = ChecksumUtils.calc(fileEntry.getValue(), Arrays.asList("SHA-512", "SHA-256", "SHA-1", "MD5"));

            for (Entry<String, Object> entry : checksums.entrySet()) {
                if (entry.getValue() instanceof Throwable) {
                    throw (Throwable) entry.getValue();
                }
                String actual = entry.getValue().toString();
                String expected = sums.get(fileEntry.getKey()).get(entry.getKey());
                assertEquals(
                        String.format(
                                "checksums do not match for '%s', algorithm '%s'",
                                fileEntry.getValue().getName(), entry.getKey()),
                        expected,
                        actual);
            }
            assertTrue("Could not delete file", fileEntry.getValue().delete());
        }
    }

    @Test
    public void testFileHandleLeakage() throws IOException {
        for (File file : files.values()) {
            for (int i = 0; i < 150; i++) {
                ChecksumUtils.calc(file, Arrays.asList("SHA-512", "SHA-256", "SHA-1", "MD5"));
            }
            assertTrue("Could not delete file", file.delete());
        }
    }

    @Test
    public void testRead() throws IOException {
        for (Map<String, String> checksums : sums.values()) {
            String sha512 = checksums.get("SHA-512");
            String sha256 = checksums.get("SHA-256");
            String sha1 = checksums.get("SHA-1");
            String md5 = checksums.get("MD5");

            File sha512File = createTempFile(sha512);
            File sha256File = createTempFile(sha256);
            File sha1File = createTempFile(sha1);
            File md5File = createTempFile(md5);

            assertEquals(sha512, ChecksumUtils.read(sha512File));
            assertEquals(sha256, ChecksumUtils.read(sha256File));
            assertEquals(sha1, ChecksumUtils.read(sha1File));
            assertEquals(md5, ChecksumUtils.read(md5File));

            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha512)", sha512File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha256)", sha256File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha1)", sha1File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.md5)", md5File.delete());
        }
    }

    @Test
    public void testReadSpaces() throws IOException {
        for (Map<String, String> checksums : sums.values()) {
            String sha512 = checksums.get("SHA-512");
            String sha256 = checksums.get("SHA-256");
            String sha1 = checksums.get("SHA-1");
            String md5 = checksums.get("MD5");

            File sha512File = createTempFile("sha512-checksum = " + sha512);
            File sha256File = createTempFile("sha256-checksum = " + sha256);
            File sha1File = createTempFile("sha1-checksum = " + sha1);
            File md5File = createTempFile(md5 + " test");

            assertEquals(sha512, ChecksumUtils.read(sha512File));
            assertEquals(sha256, ChecksumUtils.read(sha256File));
            assertEquals(sha1, ChecksumUtils.read(sha1File));
            assertEquals(md5, ChecksumUtils.read(md5File));

            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha512)", sha512File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha256)", sha256File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.sha1)", sha1File.delete());
            assertTrue("ChecksumUtils leaks file handles (cannot delete checksums.md5)", md5File.delete());
        }
    }

    @Test
    public void testReadEmptyFile() throws IOException {
        File file = createTempFile("");

        assertEquals("", ChecksumUtils.read(file));

        assertTrue("ChecksumUtils leaks file handles (cannot delete checksum.empty)", file.delete());
    }

    @Test
    public void testToHexString() {
        assertNull(ChecksumUtils.toHexString(null));
        assertEquals("", ChecksumUtils.toHexString(new byte[] {}));
        assertEquals("00", ChecksumUtils.toHexString(new byte[] {0}));
        assertEquals("ff", ChecksumUtils.toHexString(new byte[] {-1}));
        assertEquals("00017f", ChecksumUtils.toHexString(new byte[] {0, 1, 127}));
    }

    @Test
    public void testFromHexString() {
        assertNull(ChecksumUtils.toHexString(null));
        assertArrayEquals(new byte[] {}, ChecksumUtils.fromHexString(""));
        assertArrayEquals(new byte[] {0}, ChecksumUtils.fromHexString("00"));
        assertArrayEquals(new byte[] {-1}, ChecksumUtils.fromHexString("ff"));
        assertArrayEquals(new byte[] {0, 1, 127}, ChecksumUtils.fromHexString("00017f"));
    }

    @Test
    public void testCalcWithByteArray() throws Throwable {
        Map<String, Object> checksums = null;

        for (Map.Entry<String, byte[]> bytesEntry : bytes.entrySet()) {
            checksums = ChecksumUtils.calc(bytesEntry.getValue(), Arrays.asList("SHA-512", "SHA-256", "SHA-1", "MD5"));

            for (Entry<String, Object> entry : checksums.entrySet()) {
                if (entry.getValue() instanceof Throwable) {
                    throw (Throwable) entry.getValue();
                }
                String actual = entry.getValue().toString();
                String expected = sums.get(bytesEntry.getKey()).get(entry.getKey());
                assertEquals(
                        String.format(
                                "checksums do not match for '%s', algorithm '%s'", bytesEntry.getKey(), entry.getKey()),
                        expected,
                        actual);
            }
        }
    }

    private byte[] writeBytes(byte[] pattern, int repeat) {
        byte[] result = new byte[pattern.length * repeat];
        for (int i = 0; i < repeat; i++) {
            System.arraycopy(pattern, 0, result, i * pattern.length, pattern.length);
        }
        return result;
    }
}
