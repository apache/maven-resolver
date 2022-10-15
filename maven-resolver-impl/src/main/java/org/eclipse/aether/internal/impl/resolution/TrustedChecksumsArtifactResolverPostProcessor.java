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
package org.eclipse.aether.internal.impl.resolution;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * Artifact resolver processor that verifies the checksums of all resolved artifacts against trusted checksums. Is also
 * able to "record" (calculate and write them) to trusted checksum sources, that do support this operation.
 *
 * @since TBD
 */
@Singleton
@Named(TrustedChecksumsArtifactResolverPostProcessor.NAME)
public final class TrustedChecksumsArtifactResolverPostProcessor extends ArtifactResolverPostProcessorSupport {
    public static final String NAME = "trusted-checksums";

    private static final String CONF_CHECKSUM_ALGORITHMS = "checksumAlgorithms";

    private static final String DEFAULT_CHECKSUM_ALGORITHMS = "SHA-1";

    private static final String CONF_FAIL_IF_MISSING = "failIfMissing";

    private static final String CONF_RECORD = "record";

    private static final String CHECKSUM_ALGORITHMS_CACHE_KEY =
            TrustedChecksumsArtifactResolverPostProcessor.class.getName() + ".checksumAlgorithms";

    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;

    private final Map<String, TrustedChecksumsSource> trustedChecksumsSources;

    @Inject
    public TrustedChecksumsArtifactResolverPostProcessor(
            ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector,
            Map<String, TrustedChecksumsSource> trustedChecksumsSources) {
        super(NAME);
        this.checksumAlgorithmFactorySelector = requireNonNull(checksumAlgorithmFactorySelector);
        this.trustedChecksumsSources = requireNonNull(trustedChecksumsSources);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doProcess(RepositorySystemSession session, List<ArtifactResult> artifactResults) {
        final List<ChecksumAlgorithmFactory> checksumAlgorithms = (List<ChecksumAlgorithmFactory>) session.getData()
                .computeIfAbsent(
                        CHECKSUM_ALGORITHMS_CACHE_KEY,
                        () -> checksumAlgorithmFactorySelector.selectList(
                                ConfigUtils.parseCommaSeparatedUniqueNames(ConfigUtils.getString(
                                        session, DEFAULT_CHECKSUM_ALGORITHMS, CONF_CHECKSUM_ALGORITHMS))));

        final boolean failIfMissing = ConfigUtils.getBoolean(session, false, configPropKey(CONF_FAIL_IF_MISSING));
        final boolean record = ConfigUtils.getBoolean(session, false, configPropKey(CONF_RECORD));

        for (ArtifactResult artifactResult : artifactResults) {
            if (artifactResult.isResolved()) {
                if (record) {
                    recordArtifactChecksums(session, artifactResult, checksumAlgorithms);
                } else if (!validateArtifactChecksums(session, artifactResult, checksumAlgorithms, failIfMissing)) {
                    artifactResult.setArtifact(artifactResult.getArtifact().setFile(null)); // make it unresolved
                }
            }
        }
    }

    /**
     * Calculates and records checksums into trusted sources that support writing.
     */
    private void recordArtifactChecksums(
            RepositorySystemSession session,
            ArtifactResult artifactResult,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
        Artifact artifact = artifactResult.getArtifact();
        ArtifactRepository artifactRepository = artifactResult.getRepository();

        try {
            final Map<String, String> calculatedChecksums =
                    ChecksumAlgorithmHelper.calculate(artifact.getFile(), checksumAlgorithmFactories);

            for (TrustedChecksumsSource trustedChecksumsSource : trustedChecksumsSources.values()) {
                try (TrustedChecksumsSource.Writer writer =
                        trustedChecksumsSource.getTrustedArtifactChecksumsWriter(session)) {
                    if (writer != null) {
                        writer.addTrustedArtifactChecksums(
                                artifact, artifactRepository, checksumAlgorithmFactories, calculatedChecksums);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not calculate amd write required checksums for " + artifact.getFile(), e);
        }
    }

    /**
     * Validates trusted checksums against {@link ArtifactResult}, returns {@code true} denoting "valid" checksums or
     * {@code false} denoting "invalid" checksums.
     */
    private boolean validateArtifactChecksums(
            RepositorySystemSession session,
            ArtifactResult artifactResult,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
            boolean failIfMissing) {
        Artifact artifact = artifactResult.getArtifact();
        ArtifactRepository artifactRepository = artifactResult.getRepository();

        boolean valid = true;
        boolean validated = false;
        try {
            // full set: calculate all algorithms we were asked for
            final Map<String, String> calculatedChecksums =
                    ChecksumAlgorithmHelper.calculate(artifact.getFile(), checksumAlgorithmFactories);

            for (Map.Entry<String, TrustedChecksumsSource> entry : trustedChecksumsSources.entrySet()) {
                final String trustedSourceName = entry.getKey();
                final TrustedChecksumsSource trustedChecksumsSource = entry.getValue();

                // upper bound set: ask source for checksums, ideally same as calculatedChecksums but may be less
                Map<String, String> trustedChecksums = trustedChecksumsSource.getTrustedArtifactChecksums(
                        session, artifact, artifactRepository, checksumAlgorithmFactories);

                if (trustedChecksums == null) {
                    continue; // not enabled
                }
                validated = true;

                if (!calculatedChecksums.equals(trustedChecksums)) {
                    Set<String> missingTrustedAlg = new HashSet<>(calculatedChecksums.keySet());
                    missingTrustedAlg.removeAll(trustedChecksums.keySet());

                    if (!missingTrustedAlg.isEmpty() && failIfMissing) {
                        artifactResult.addException(new ChecksumFailureException("Missing from " + trustedSourceName
                                + " trusted checksum(s) " + missingTrustedAlg + " for artifact "
                                + ArtifactIdUtils.toId(artifact)));
                        valid = false;
                    }

                    // compare values but only present ones, failIfMissing handled above
                    // we still want to report all: algX - missing, algY - mismatch, etc
                    for (ChecksumAlgorithmFactory checksumAlgorithmFactory : checksumAlgorithmFactories) {
                        String calculatedChecksum = calculatedChecksums.get(checksumAlgorithmFactory.getName());
                        String trustedChecksum = trustedChecksums.get(checksumAlgorithmFactory.getName());
                        if (trustedChecksum != null && !Objects.equals(calculatedChecksum, trustedChecksum)) {
                            artifactResult.addException(new ChecksumFailureException("Artifact "
                                    + ArtifactIdUtils.toId(artifact) + " trusted checksum mismatch: "
                                    + trustedSourceName + "=" + trustedChecksum + "; calculated="
                                    + calculatedChecksum));
                            valid = false;
                        }
                    }
                }
            }

            if (!validated && failIfMissing) {
                artifactResult.addException(new ChecksumFailureException(
                        "There are no enabled trusted checksums" + " source(s) to validate against."));
                valid = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return valid;
    }
}
