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
package org.eclipse.aether.spi.connector.checksum;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper for checksum operations.
 *
 * @since 1.8.0
 */
public final class ChecksumAlgorithmHelper {
    private ChecksumAlgorithmHelper() {
        // nop
    }

    /**
     * Calculates checksums for specified data.
     *
     * @param data        The content for which to calculate checksums, must not be {@code null}.
     * @param factories   The checksum algorithm factories to use, must not be {@code null}.
     * @return The calculated checksums, indexed by algorithm name, or the exception that occurred while trying to
     * calculate it, never {@code null}.
     * @throws IOException In case of any problem.
     */
    public static Map<String, String> calculate(byte[] data, List<ChecksumAlgorithmFactory> factories)
            throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return calculate(inputStream, factories);
        }
    }

    /**
     * Calculates checksums for specified file.
     *
     * @param file        The file for which to calculate checksums, must not be {@code null}.
     * @param factories   The checksum algorithm factories to use, must not be {@code null}.
     * @return The calculated checksums, indexed by algorithm name, or the exception that occurred while trying to
     * calculate it, never {@code null}.
     * @throws IOException In case of any problem.
     */
    public static Map<String, String> calculate(File file, List<ChecksumAlgorithmFactory> factories)
            throws IOException {
        return calculate(file.toPath(), factories);
    }

    /**
     * Calculates checksums for specified file.
     *
     * @param path        The file for which to calculate checksums, must not be {@code null}.
     * @param factories   The checksum algorithm factories to use, must not be {@code null}.
     * @return The calculated checksums, indexed by algorithm name, or the exception that occurred while trying to
     * calculate it, never {@code null}.
     * @throws IOException In case of any problem.
     * @since 2.0.0
     */
    public static Map<String, String> calculate(Path path, List<ChecksumAlgorithmFactory> factories)
            throws IOException {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            return calculate(inputStream, factories);
        }
    }

    private static Map<String, String> calculate(InputStream inputStream, List<ChecksumAlgorithmFactory> factories)
            throws IOException {
        LinkedHashMap<String, ChecksumAlgorithm> algorithms = new LinkedHashMap<>();
        factories.forEach(f -> algorithms.put(f.getName(), f.getAlgorithm()));
        final byte[] buffer = new byte[1024 * 32];
        for (; ; ) {
            int read = inputStream.read(buffer);
            if (read < 0) {
                break;
            }
            for (ChecksumAlgorithm checksumAlgorithm : algorithms.values()) {
                checksumAlgorithm.update(ByteBuffer.wrap(buffer, 0, read));
            }
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        algorithms.forEach((k, v) -> result.put(k, v.checksum()));
        return result;
    }
}
