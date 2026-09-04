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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A utility class to assist in the verification and generation of checksums.
 */
public final class ChecksumUtils {

    /**
     * Upper bound (in characters) for data read from a checksum file, see {@link #read(File)}. Every sane
     * checksum file format fits well within this limit; longer input is rejected as malformed instead of being
     * buffered into memory.
     */
    private static final int MAX_CHECKSUM_FILE_CHARS = 8192;

    private ChecksumUtils() {
        // hide constructor
    }

    /**
     * Extracts the checksum from the specified file.
     *
     * @param checksumFile The path to the checksum file, must not be {@code null}.
     * @return The checksum stored in the file, never {@code null}.
     * @throws IOException If the checksum does not exist or could not be read for other reasons.
     * @deprecated Use SPI FileProcessor to read and write checksum files.
     */
    @Deprecated
    public static String read(File checksumFile) throws IOException {
        String checksum;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(checksumFile), StandardCharsets.UTF_8), 512)) {
            checksum = readFirstNonEmptyLine(br, checksumFile.toString());
        }

        if (isAlgorithmHeaderFormat(checksum)) {
            int lastSpacePos = checksum.lastIndexOf(' ');
            checksum = checksum.substring(lastSpacePos + 1);
        } else {
            int spacePos = checksum.indexOf(' ');

            if (spacePos != -1) {
                checksum = checksum.substring(0, spacePos);
            }
        }

        return checksum;
    }

    /**
     * Reads the first non-empty line, enforcing {@link #MAX_CHECKSUM_FILE_CHARS} on the total amount of data
     * consumed. Returns the trimmed line, or an empty string if the stream holds no non-empty line.
     */
    private static String readFirstNonEmptyLine(BufferedReader reader, String source) throws IOException {
        StringBuilder buffer = new StringBuilder(64);
        int read = 0;
        int c;
        while ((c = reader.read()) != -1) {
            if (++read > MAX_CHECKSUM_FILE_CHARS) {
                throw new IOException("Checksum file " + source + " is malformed: longer than "
                        + MAX_CHECKSUM_FILE_CHARS + " characters");
            }
            if (c == '\n' || c == '\r') {
                String line = buffer.toString().trim();
                if (!line.isEmpty()) {
                    return line;
                }
                buffer.setLength(0);
            } else {
                buffer.append((char) c);
            }
        }
        return buffer.toString().trim();
    }

    /**
     * Non-backtracking equivalent of {@code line.matches(".+= [0-9A-Fa-f]+")}: at least one character, followed
     * by "= ", followed by one or more hex digits reaching the end of the line ("&lt;algorithm&gt; (&lt;file&gt;)
     * = &lt;hex&gt;" style checksum lines).
     */
    private static boolean isAlgorithmHeaderFormat(String line) {
        int lastSpacePos = line.lastIndexOf(' ');
        if (lastSpacePos < 2 || lastSpacePos == line.length() - 1 || line.charAt(lastSpacePos - 1) != '=') {
            return false;
        }
        for (int i = lastSpacePos + 1; i < line.length(); i++) {
            char ch = line.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculates checksums for the specified file.
     *
     * @param dataFile The file for which to calculate checksums, must not be {@code null}.
     * @param algos The names of checksum algorithms (cf. {@link MessageDigest#getInstance(String)} to use, must not be
     *            {@code null}.
     * @return The calculated checksums, indexed by algorithm name, or the exception that occurred while trying to
     *         calculate it, never {@code null}.
     * @throws IOException If the data file could not be read.
     * @deprecated Use SPI checksum selector instead.
     */
    @Deprecated
    public static Map<String, Object> calc(File dataFile, Collection<String> algos) throws IOException {
        return calc(new FileInputStream(dataFile), algos);
    }

    /**
     * @deprecated Use SPI checksum selector instead.
     */
    @Deprecated
    public static Map<String, Object> calc(byte[] dataBytes, Collection<String> algos) throws IOException {
        return calc(new ByteArrayInputStream(dataBytes), algos);
    }

    private static Map<String, Object> calc(InputStream data, Collection<String> algos) throws IOException {
        Map<String, Object> results = new LinkedHashMap<>();

        Map<String, MessageDigest> digests = new LinkedHashMap<>();
        for (String algo : algos) {
            try {
                digests.put(algo, MessageDigest.getInstance(algo));
            } catch (NoSuchAlgorithmException e) {
                results.put(algo, e);
            }
        }

        try (InputStream in = data) {
            for (byte[] buffer = new byte[32 * 1024]; ; ) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                for (MessageDigest digest : digests.values()) {
                    digest.update(buffer, 0, read);
                }
            }
        }

        for (Map.Entry<String, MessageDigest> entry : digests.entrySet()) {
            byte[] bytes = entry.getValue().digest();

            results.put(entry.getKey(), toHexString(bytes));
        }

        return results;
    }

    /**
     * Creates a hexadecimal representation of the specified bytes. Each byte is converted into a two-digit hex number
     * and appended to the result with no separator between consecutive bytes.
     *
     * @param bytes The bytes to represent in hex notation, may be be {@code null}.
     * @return The hexadecimal representation of the input or {@code null} if the input was {@code null}.
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder buffer = new StringBuilder(bytes.length * 2);

        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            if (b < 0x10) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b));
        }

        return buffer.toString();
    }

    /**
     * Creates a byte array out of hexadecimal representation of the specified bytes. If input string is {@code null},
     * {@code null} is returned. Input value must have even length (due hex encoding = 2 chars one byte).
     *
     * @param hexString The hexString to convert to byte array, may be {@code null}.
     * @return The byte array of the input or {@code null} if the input was {@code null}.
     * @since 1.8.0
     */
    public static byte[] fromHexString(String hexString) {
        if (hexString == null) {
            return null;
        }
        if (hexString.isEmpty()) {
            return new byte[] {};
        }
        int len = hexString.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hexString length not even");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)
                    ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
