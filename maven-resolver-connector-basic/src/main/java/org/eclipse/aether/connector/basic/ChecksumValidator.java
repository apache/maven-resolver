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
package org.eclipse.aether.connector.basic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy.ChecksumKind;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.ChecksumLocation;
import org.eclipse.aether.spi.io.ChecksumProcessor;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs checksum validation for a downloaded file.
 */
final class ChecksumValidator {

    interface ChecksumFetcher {

        /**
         * Fetches the checksums from remote location into provided local file. The checksums fetched in this way
         * are of kind {@link ChecksumKind#REMOTE_EXTERNAL}.
         */
        boolean fetchChecksum(URI remote, Path local) throws Exception;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecksumValidator.class);

    private final Path dataPath;

    private final Collection<ChecksumAlgorithmFactory> checksumAlgorithmFactories;

    private final ChecksumProcessor checksumProcessor;

    private final ChecksumFetcher checksumFetcher;

    private final ChecksumPolicy checksumPolicy;

    private final Map<String, String> providedChecksums;

    private final Collection<ChecksumLocation> checksumLocations;

    private final Map<Path, String> checksumExpectedValues;

    ChecksumValidator(
            Path dataPath,
            Collection<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
            ChecksumProcessor checksumProcessor,
            ChecksumFetcher checksumFetcher,
            ChecksumPolicy checksumPolicy,
            Map<String, String> providedChecksums,
            Collection<ChecksumLocation> checksumLocations) {
        this.dataPath = dataPath;
        this.checksumAlgorithmFactories = checksumAlgorithmFactories;
        this.checksumProcessor = checksumProcessor;
        this.checksumFetcher = checksumFetcher;
        this.checksumPolicy = checksumPolicy;
        this.providedChecksums = providedChecksums;
        this.checksumLocations = checksumLocations;
        this.checksumExpectedValues = new HashMap<>();
    }

    public ChecksumCalculator newChecksumCalculator(Path targetFile) {
        if (checksumPolicy != null) {
            return ChecksumCalculator.newInstance(targetFile, checksumAlgorithmFactories);
        }
        return null;
    }

    public void validate(Map<String, ?> actualChecksums, Map<String, ?> includedChecksums)
            throws ChecksumFailureException {
        if (checksumPolicy == null) {
            return;
        }
        if (providedChecksums != null && validateChecksums(actualChecksums, ChecksumKind.PROVIDED, providedChecksums)) {
            return;
        }
        if (includedChecksums != null
                && validateChecksums(actualChecksums, ChecksumKind.REMOTE_INCLUDED, includedChecksums)) {
            return;
        }
        if (!checksumLocations.isEmpty()) {
            if (validateExternalChecksums(actualChecksums)) {
                return;
            }
            checksumPolicy.onNoMoreChecksums();
        }
    }

    private boolean validateChecksums(Map<String, ?> actualChecksums, ChecksumKind kind, Map<String, ?> checksums)
            throws ChecksumFailureException {
        for (Map.Entry<String, ?> entry : checksums.entrySet()) {
            String algo = entry.getKey();
            Object calculated = actualChecksums.get(algo);
            if (!(calculated instanceof String)) {
                continue;
            }
            ChecksumAlgorithmFactory checksumAlgorithmFactory = checksumAlgorithmFactories.stream()
                    .filter(a -> a.getName().equals(algo))
                    .findFirst()
                    .orElse(null);
            if (checksumAlgorithmFactory == null) {
                continue;
            }

            String actual = String.valueOf(calculated);
            String expected = entry.getValue().toString();
            checksumExpectedValues.put(getChecksumPath(checksumAlgorithmFactory), expected);

            if (!isEqualChecksum(expected, actual)) {
                checksumPolicy.onChecksumMismatch(
                        checksumAlgorithmFactory.getName(),
                        kind,
                        new ChecksumFailureException(expected, kind.name(), actual));
            } else if (checksumPolicy.onChecksumMatch(checksumAlgorithmFactory.getName(), kind)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateExternalChecksums(Map<String, ?> actualChecksums) throws ChecksumFailureException {
        for (ChecksumLocation checksumLocation : checksumLocations) {
            ChecksumAlgorithmFactory factory = checksumLocation.getChecksumAlgorithmFactory();
            Object calculated = actualChecksums.get(factory.getName());
            if (calculated instanceof Exception) {
                checksumPolicy.onChecksumError(
                        factory.getName(), ChecksumKind.REMOTE_EXTERNAL, new ChecksumFailureException((Exception)
                                calculated));
                continue;
            }
            Path checksumFile = getChecksumPath(checksumLocation.getChecksumAlgorithmFactory());
            try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
                Path tmp = tempFile.getPath();
                try {
                    if (!checksumFetcher.fetchChecksum(checksumLocation.getLocation(), tmp)) {
                        continue;
                    }
                } catch (Exception e) {
                    checksumPolicy.onChecksumError(
                            factory.getName(), ChecksumKind.REMOTE_EXTERNAL, new ChecksumFailureException(e));
                    continue;
                }

                String actual = String.valueOf(calculated);
                String expected = checksumProcessor.readChecksum(tmp);
                checksumExpectedValues.put(checksumFile, expected);

                if (!isEqualChecksum(expected, actual)) {
                    checksumPolicy.onChecksumMismatch(
                            factory.getName(),
                            ChecksumKind.REMOTE_EXTERNAL,
                            new ChecksumFailureException(expected, ChecksumKind.REMOTE_EXTERNAL.name(), actual));
                } else if (checksumPolicy.onChecksumMatch(factory.getName(), ChecksumKind.REMOTE_EXTERNAL)) {
                    return true;
                }
            } catch (IOException e) {
                checksumPolicy.onChecksumError(
                        factory.getName(), ChecksumKind.REMOTE_EXTERNAL, new ChecksumFailureException(e));
            }
        }
        return false;
    }

    private static boolean isEqualChecksum(String expected, String actual) {
        return expected.equalsIgnoreCase(actual);
    }

    private Path getChecksumPath(ChecksumAlgorithmFactory factory) {
        return dataPath.getParent().resolve(dataPath.getFileName() + "." + factory.getFileExtension());
    }

    public void retry() {
        checksumPolicy.onTransferRetry();
        checksumExpectedValues.clear();
    }

    public boolean handle(ChecksumFailureException exception) {
        return checksumPolicy.onTransferChecksumFailure(exception);
    }

    public void commit() {
        for (Map.Entry<Path, String> entry : checksumExpectedValues.entrySet()) {
            Path checksumFile = entry.getKey();
            try {
                checksumProcessor.writeChecksum(checksumFile, entry.getValue());
            } catch (IOException e) {
                LOGGER.debug("Failed to write checksum file {}", checksumFile, e);
                throw new UncheckedIOException(e);
            }
        }
        checksumExpectedValues.clear();
    }
}
