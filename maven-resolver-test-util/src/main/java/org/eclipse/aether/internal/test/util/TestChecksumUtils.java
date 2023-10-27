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
package org.eclipse.aether.internal.test.util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Provides utility methods to calculate checksums.
 */
public class TestChecksumUtils {
    public static Map<String, Object> calc(File dataFile, Collection<String> algos) throws IOException {
        return calc(new FileInputStream(dataFile), algos);
    }

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

    @SuppressWarnings("checkstyle:magicnumber")
    private static String toHexString(byte[] bytes) {
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
}
