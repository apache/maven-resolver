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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A simple digester utility for Strings. Uses {@link MessageDigest} for requested algorithm. Supports one-pass or
 * several rounds of updates, and as result emits hex encoded String.
 *
 * @since 1.9.0
 */
public final class StringDigestUtil {
    private final MessageDigest digest;

    /**
     * Constructs instance with given algorithm.
     *
     * @see #sha1()
     * @see #sha1(String)
     */
    public StringDigestUtil(final String alg) {
        try {
            this.digest = MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Not supported digest algorithm: " + alg);
        }
    }

    /**
     * Updates instance with passed in string.
     */
    public StringDigestUtil update(String data) {
        if (data != null && !data.isEmpty()) {
            digest.update(data.getBytes(StandardCharsets.UTF_8));
        }
        return this;
    }

    /**
     * Returns the digest of all strings passed via {@link #update(String)} as hex string. There is no state preserved
     * and due implementation of {@link MessageDigest#digest()}, same applies here: this instance "resets" itself.
     * Hence, the digest hex encoded string is returned only once.
     *
     * @see MessageDigest#digest()
     */
    public String digest() {
        return toHexString(digest.digest());
    }

    /**
     * Helper method to create {@link StringDigestUtil} using SHA-1 digest algorithm.
     */
    public static StringDigestUtil sha1() {
        return new StringDigestUtil("SHA-1");
    }

    /**
     * Helper method to calculate SHA-1 digest and hex encode it.
     */
    public static String sha1(final String string) {
        return sha1().update(string).digest();
    }

    /**
     * Creates a hexadecimal representation of the specified bytes. Each byte is converted into a two-digit hex number
     * and appended to the result with no separator between consecutive bytes.
     *
     * @param bytes The bytes to represent in hex notation, may be {@code null}.
     * @return The hexadecimal representation of the input or {@code null} if the input was {@code null}.
     * @since 2.0.0
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
     * @since 2.0.0
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
