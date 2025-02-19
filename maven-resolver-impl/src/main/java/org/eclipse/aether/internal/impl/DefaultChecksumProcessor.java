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
    private final PathProcessor pathProcessor;

    @Inject
    public DefaultChecksumProcessor(PathProcessor pathProcessor) {
        this.pathProcessor = requireNonNull(pathProcessor);
    }

    @Override
    public String readChecksum(final Path checksumPath) throws IOException {
        // for now do exactly same as happened before, but FileProcessor is a component and can be replaced
        String checksum = "";
        try (BufferedReader br = Files.newBufferedReader(checksumPath, StandardCharsets.UTF_8)) {
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    checksum = line;
                    break;
                }
            }
        }

        if (checksum.matches(".+= [0-9A-Fa-f]+")) {
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

    @Override
    public void writeChecksum(Path target, String checksum) throws IOException {
        // for now do exactly same as happened before, but FileProcessor is a component and can be replaced
        pathProcessor.write(target, checksum);
    }
}
