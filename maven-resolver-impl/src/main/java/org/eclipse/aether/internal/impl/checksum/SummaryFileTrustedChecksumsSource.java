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
package org.eclipse.aether.internal.impl.checksum;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.LocalPathComposer;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Compact file {@link FileTrustedChecksumsSourceSupport} implementation that use specified directory as base
 * directory, where it expects a "summary" file named as "checksums.${checksumExt}" for each checksum algorithm.
 * File format is GNU Coreutils compatible: each line holds checksum followed by two spaces and artifact relative path
 * (from local repository root, without leading "./"). This means that trusted checksums summary file can be used to
 * validate artifacts or generate it using standard GNU tools like GNU {@code sha1sum} is (for BSD derivatives same
 * file can be used with {@code -r} switch).
 * <p>
 * The format supports comments "#" (hash) and empty lines for easier structuring the file content, and both are
 * ignored. Also, their presence makes the summary file incompatible with GNU Coreutils format. On save of the
 * summary file, the comments and empty lines are lost, and file is sorted by path names for easier diffing
 * (2nd column in file).
 * <p>
 * The source by default is "origin aware", and it will factor in origin repository ID as well into summary file name,
 * for example "checksums-central.sha256".
 * <p>
 * Example commands for managing summary file (in examples will use repository ID "central"):
 * <ul>
 *     <li>To create summary file: {@code find * -not -name "checksums-central.sha256" -type f -print0 |
 *       xargs -0 sha256sum | sort -k 2 > checksums-central.sha256}</li>
 *     <li>To verify artifacts using summary file: {@code sha256sum --quiet -c checksums-central.sha256}</li>
 * </ul>
 * <p>
 * The checksums summary file is lazily loaded and remains cached during lifetime of the component, so file changes
 * during lifecycle of the component are not picked up. This implementation can be simultaneously used to lookup and
 * also record checksums. The recorded checksums will become visible for every session, and will be flushed
 * at repository system shutdown, merged with existing ones on disk.
 * <p>
 * The name of this implementation is "summaryFile".
 *
 * @see <a href="https://man7.org/linux/man-pages/man1/sha1sum.1.html">sha1sum man page</a>
 * @see <a href="https://www.gnu.org/software/coreutils/manual/coreutils.html#md5sum-invocation">GNU Coreutils: md5sum</a>
 * @since 1.9.0
 */
@Singleton
@Named(SummaryFileTrustedChecksumsSource.NAME)
public final class SummaryFileTrustedChecksumsSource extends FileTrustedChecksumsSourceSupport {
    public static final String NAME = "summaryFile";

    private static final String CONFIG_PROPS_PREFIX =
            FileTrustedChecksumsSourceSupport.CONFIG_PROPS_PREFIX + NAME + ".";

    /**
     * Is checksum source enabled?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_ENABLED = FileTrustedChecksumsSourceSupport.CONFIG_PROPS_PREFIX + NAME;

    /**
     * The basedir where checksums are. If relative, is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #LOCAL_REPO_PREFIX_DIR}
     */
    public static final String CONFIG_PROP_BASEDIR = CONFIG_PROPS_PREFIX + "basedir";

    public static final String LOCAL_REPO_PREFIX_DIR = ".checksums";

    /**
     * Is source origin aware?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue true
     */
    public static final String CONFIG_PROP_ORIGIN_AWARE = CONFIG_PROPS_PREFIX + "originAware";

    public static final String CHECKSUMS_FILE_PREFIX = "checksums";

    private static final Logger LOGGER = LoggerFactory.getLogger(SummaryFileTrustedChecksumsSource.class);

    private final LocalPathComposer localPathComposer;

    private final RepositorySystemLifecycle repositorySystemLifecycle;

    private final PathProcessor pathProcessor;

    private final ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> checksums;

    private final ConcurrentHashMap<Path, Boolean> changedChecksums;

    private final AtomicBoolean onShutdownHandlerRegistered;

    @Inject
    public SummaryFileTrustedChecksumsSource(
            LocalPathComposer localPathComposer,
            RepositorySystemLifecycle repositorySystemLifecycle,
            PathProcessor pathProcessor) {
        this.localPathComposer = requireNonNull(localPathComposer);
        this.repositorySystemLifecycle = requireNonNull(repositorySystemLifecycle);
        this.pathProcessor = requireNonNull(pathProcessor);
        this.checksums = new ConcurrentHashMap<>();
        this.changedChecksums = new ConcurrentHashMap<>();
        this.onShutdownHandlerRegistered = new AtomicBoolean(false);
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_ENABLED);
    }

    private boolean isOriginAware(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, true, CONFIG_PROP_ORIGIN_AWARE);
    }

    @Override
    protected Map<String, String> doGetTrustedArtifactChecksums(
            RepositorySystemSession session,
            Artifact artifact,
            ArtifactRepository artifactRepository,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
        final HashMap<String, String> result = new HashMap<>();
        final Path basedir = getBasedir(session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, false);
        if (Files.isDirectory(basedir)) {
            final String artifactPath = localPathComposer.getPathForArtifact(artifact, false);
            final boolean originAware = isOriginAware(session);
            for (ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories) {
                Path summaryFile = summaryFile(
                        basedir,
                        originAware,
                        repositoryKey(session, artifactRepository),
                        checksumAlgorithmFactory.getFileExtension());
                ConcurrentHashMap<String, String> algorithmChecksums =
                        checksums.computeIfAbsent(summaryFile, f -> loadProvidedChecksums(summaryFile));
                String checksum = algorithmChecksums.get(artifactPath);
                if (checksum != null) {
                    result.put(checksumAlgorithmFactory.getName(), checksum);
                }
            }
        }
        return result;
    }

    @Override
    protected Writer doGetTrustedArtifactChecksumsWriter(RepositorySystemSession session) {
        if (onShutdownHandlerRegistered.compareAndSet(false, true)) {
            repositorySystemLifecycle.addOnSystemEndedHandler(this::saveRecordedLines);
        }
        return new SummaryFileWriter(
                checksums,
                getBasedir(session, LOCAL_REPO_PREFIX_DIR, CONFIG_PROP_BASEDIR, true),
                isOriginAware(session),
                r -> repositoryKey(session, r));
    }

    /**
     * Returns the summary file path. The file itself and its parent directories may not exist, this method merely
     * calculate the path.
     */
    private Path summaryFile(Path basedir, boolean originAware, String safeRepositoryId, String checksumExtension) {
        String fileName = CHECKSUMS_FILE_PREFIX;
        if (originAware) {
            fileName += "-" + safeRepositoryId;
        }
        return basedir.resolve(fileName + "." + checksumExtension);
    }

    private ConcurrentHashMap<String, String> loadProvidedChecksums(Path summaryFile) {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
        if (Files.isRegularFile(summaryFile)) {
            try (BufferedReader reader = Files.newBufferedReader(summaryFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("#") && !line.isEmpty()) {
                        String[] parts = line.split("  ", 2);
                        if (parts.length == 2) {
                            String newChecksum = parts[0];
                            String artifactPath = parts[1];
                            String oldChecksum = result.put(artifactPath, newChecksum);
                            if (oldChecksum != null) {
                                if (Objects.equals(oldChecksum, newChecksum)) {
                                    LOGGER.warn(
                                            "Checksums file '{}' contains duplicate checksums for artifact {}: {}",
                                            summaryFile,
                                            artifactPath,
                                            oldChecksum);
                                } else {
                                    LOGGER.warn(
                                            "Checksums file '{}' contains different checksums for artifact {}: "
                                                    + "old '{}' replaced by new '{}'",
                                            summaryFile,
                                            artifactPath,
                                            oldChecksum,
                                            newChecksum);
                                }
                            }
                        } else {
                            LOGGER.warn("Checksums file '{}' ignored malformed line '{}'", summaryFile, line);
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            LOGGER.info("Loaded {} trusted checksums from {}", result.size(), summaryFile);
        }
        return result;
    }

    private class SummaryFileWriter implements Writer {
        private final ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache;

        private final Path basedir;

        private final boolean originAware;

        private final Function<ArtifactRepository, String> repositoryKeyFunction;

        private SummaryFileWriter(
                ConcurrentHashMap<Path, ConcurrentHashMap<String, String>> cache,
                Path basedir,
                boolean originAware,
                Function<ArtifactRepository, String> repositoryKeyFunction) {
            this.cache = cache;
            this.basedir = basedir;
            this.originAware = originAware;
            this.repositoryKeyFunction = repositoryKeyFunction;
        }

        @Override
        public void addTrustedArtifactChecksums(
                Artifact artifact,
                ArtifactRepository artifactRepository,
                List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                Map<String, String> trustedArtifactChecksums) {
            String artifactPath = localPathComposer.getPathForArtifact(artifact, false);
            for (ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories) {
                Path summaryFile = summaryFile(
                        basedir,
                        originAware,
                        repositoryKeyFunction.apply(artifactRepository),
                        checksumAlgorithmFactory.getFileExtension());
                String checksum = requireNonNull(trustedArtifactChecksums.get(checksumAlgorithmFactory.getName()));

                String oldChecksum = cache.computeIfAbsent(summaryFile, k -> loadProvidedChecksums(summaryFile))
                        .put(artifactPath, checksum);

                if (oldChecksum == null) {
                    changedChecksums.put(summaryFile, Boolean.TRUE); // new
                } else if (!Objects.equals(oldChecksum, checksum)) {
                    changedChecksums.put(summaryFile, Boolean.TRUE); // replaced
                    LOGGER.info(
                            "Trusted checksum for artifact {} replaced: old {}, new {}",
                            artifact,
                            oldChecksum,
                            checksum);
                }
            }
        }
    }

    /**
     * On-close handler that saves recorded checksums, if any.
     */
    private void saveRecordedLines() {
        if (changedChecksums.isEmpty()) {
            return;
        }

        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<Path, ConcurrentHashMap<String, String>> entry : checksums.entrySet()) {
            Path summaryFile = entry.getKey();
            if (changedChecksums.get(summaryFile) != Boolean.TRUE) {
                continue;
            }
            ConcurrentHashMap<String, String> recordedLines = entry.getValue();
            if (!recordedLines.isEmpty()) {
                try {
                    ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();
                    result.putAll(loadProvidedChecksums(summaryFile));
                    result.putAll(recordedLines);

                    LOGGER.info("Saving {} checksums to '{}'", result.size(), summaryFile);
                    pathProcessor.writeWithBackup(
                            summaryFile,
                            result.entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(e -> e.getValue() + "  " + e.getKey())
                                    .collect(Collectors.joining(System.lineSeparator())));
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }
        MultiRuntimeException.mayThrow("session save checksums failure", exceptions);
    }
}
