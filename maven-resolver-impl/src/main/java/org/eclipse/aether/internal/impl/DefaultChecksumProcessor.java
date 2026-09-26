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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.spi.io.ChecksumProcessor;
import org.eclipse.aether.spi.io.PathProcessor;

import static java.util.Objects.requireNonNull;

/**
 * A utility class helping with file-based operations.
 */
@Singleton
@Named
public class DefaultChecksumProcessor implements ChecksumProcessor {
    /**
     * Upper bound (in characters) for data read from a checksum file. Every sane checksum file format fits well
     * within this limit (the longest is "SHA-512 (&lt;file name&gt;) = &lt;128 hex chars&gt;"). Checksum files
     * are fetched from remote repositories: without a bound, a hostile repository answering a checksum request
     * with a multi-gigabyte single-line body would be buffered wholesale into memory, exhausting the build JVM
     * heap. Longer input is rejected as malformed instead of being buffered.
     */
    static final int MAX_CHECKSUM_FILE_CHARS = 8192;

    private final PathProcessor pathProcessor;

    @Inject
    public DefaultChecksumProcessor(PathProcessor pathProcessor) {
        this.pathProcessor = requireNonNull(pathProcessor);
    }

    @Override
    public String readChecksum(final Path checksumPath) throws IOException {
        String checksum;
        try (BufferedReader br = Files.newBufferedReader(checksumPath, StandardCharsets.UTF_8)) {
            checksum = readFirstNonEmptyLine(br, checksumPath.toString());
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
    static String readFirstNonEmptyLine(BufferedReader reader, String source) throws IOException {
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
    static boolean isAlgorithmHeaderFormat(String line) {
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

    @Override
    public void writeChecksum(Path target, String checksum) throws IOException {
        // for now do exactly same as happened before, but FileProcessor is a component and can be replaced
        pathProcessor.write(target, checksum);
    }
}
