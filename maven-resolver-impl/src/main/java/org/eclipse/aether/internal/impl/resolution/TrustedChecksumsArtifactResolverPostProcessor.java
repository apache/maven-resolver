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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

import static java.util.Objects.requireNonNull;

/**
 * Artifact resolver processor that verifies the checksums of all resolved artifacts against trusted checksums. Is also
 * able to "record" (calculate and write them) to trusted checksum sources, that do support this operation.
 * <p>
 * It uses a list of {@link ChecksumAlgorithmFactory}ies to work with, by default SHA-1.
 * <p>
 * Configuration keys:
 * <ul>
 *     <li>{@code aether.artifactResolver.postProcessor.trustedChecksums.checksumAlgorithms} - Comma separated
 *       list of {@link ChecksumAlgorithmFactory} names to use (default "SHA-1").</li>
 *     <li>{@code aether.artifactResolver.postProcessor.trustedChecksums.failIfMissing} - To fail if artifact
 *       being validated is missing a trusted checksum (default {@code false}).</li>
 *     <li>{@code aether.artifactResolver.postProcessor.trustedChecksums.snapshots} - Should snapshot artifacts be
 *       handled (validated or recorded). Snapshots are by "best practice" in-house produced, hence should be trusted
 *       (default {@code false}).</li>
 *     <li>{@code aether.artifactResolver.postProcessor.trustedChecksums.record} - If this value set to {@code true},
 *       this component with not validate but "record" encountered artifact checksums instead
 *       (default {@code false}).</li>
 * </ul>
 * <p>
 * This component uses {@link TrustedChecksumsSource} as source of checksums for validation and also to "record" the
 * calculated checksums. To have this component usable, there must exist at least one enabled checksum source. In case
 * of multiple checksum sources enabled, ALL of them are used as source for validation or recording. This
 * implies that if two enabled checksum sources "disagree" about an artifact checksum, the validation failure is
 * inevitable.
 *
 * @since 1.9.0
 */
@Singleton
@Named(TrustedChecksumsArtifactResolverPostProcessor.NAME)
public final class TrustedChecksumsArtifactResolverPostProcessor extends ArtifactResolverPostProcessorSupport {
    public static final String NAME = "trustedChecksums";

    private static final String CONFIG_PROPS_PREFIX =
            ArtifactResolverPostProcessorSupport.CONFIG_PROPS_PREFIX + NAME + ".";

    /**
     * Is post processor enabled.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_ENABLED = ArtifactResolverPostProcessorSupport.CONFIG_PROPS_PREFIX + NAME;

    /**
     * The checksum algorithms to apply during post-processing as comma separated list.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_CHECKSUM_ALGORITHMS}
     */
    public static final String CONFIG_PROP_CHECKSUM_ALGORITHMS = CONFIG_PROPS_PREFIX + "checksumAlgorithms";

    public static final String DEFAULT_CHECKSUM_ALGORITHMS = "SHA-1";

    /**
     * The scope to apply during post-processing. Accepted values are {@code all} (is default and is what happened
     * before), and {@code project} when the scope of verification are project dependencies only (i.e. plugins are
     * not verified).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_SCOPE}
     * @since 2.0.11
     */
    public static final String CONFIG_PROP_SCOPE = CONFIG_PROPS_PREFIX + "scope";

    public static final String ALL_SCOPE = "all";

    public static final String PROJECT_SCOPE = "project";

    public static final String DEFAULT_SCOPE = ALL_SCOPE;

    /**
     * Should post processor fail resolution if checksum is missing?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_FAIL_IF_MISSING = CONFIG_PROPS_PREFIX + "failIfMissing";

    /**
     * Should post processor process snapshots as well?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_SNAPSHOTS = CONFIG_PROPS_PREFIX + "snapshots";

    /**
     * Should post processor go into "record" mode (and collect checksums instead of validate them)?
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String CONFIG_PROP_RECORD = CONFIG_PROPS_PREFIX + "record";

    private static final String CHECKSUM_ALGORITHMS_CACHE_KEY =
            TrustedChecksumsArtifactResolverPostProcessor.class.getName() + ".checksumAlgorithms";

    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;

    private final Map<String, TrustedChecksumsSource> trustedChecksumsSources;

    @Inject
    public TrustedChecksumsArtifactResolverPostProcessor(
            ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector,
            Map<String, TrustedChecksumsSource> trustedChecksumsSources) {
        this.checksumAlgorithmFactorySelector = requireNonNull(checksumAlgorithmFactorySelector);
        this.trustedChecksumsSources = requireNonNull(trustedChecksumsSources);
    }

    @Override
    protected boolean isEnabled(RepositorySystemSession session) {
        return ConfigUtils.getBoolean(session, false, CONFIG_PROP_ENABLED);
    }

    private boolean inScope(RepositorySystemSession session, ArtifactResult artifactResult) {
        String scope = ConfigUtils.getString(session, DEFAULT_SCOPE, CONFIG_PROP_SCOPE);
        if (ALL_SCOPE.equals(scope)) {
            return artifactResult.isResolved();
        } else if (PROJECT_SCOPE.equals(scope)) {
            return artifactResult.isResolved()
                    && artifactResult.getRequest().getRequestContext().startsWith("project");
        } else {
            throw new IllegalArgumentException("Unknown value for configuration " + CONFIG_PROP_SCOPE + ": " + scope);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doPostProcess(RepositorySystemSession session, List<ArtifactResult> artifactResults) {
        final List<ChecksumAlgorithmFactory> checksumAlgorithms = (List<ChecksumAlgorithmFactory>) session.getData()
                .computeIfAbsent(
                        CHECKSUM_ALGORITHMS_CACHE_KEY,
                        () -> checksumAlgorithmFactorySelector.selectList(
                                ConfigUtils.parseCommaSeparatedUniqueNames(ConfigUtils.getString(
                                        session, DEFAULT_CHECKSUM_ALGORITHMS, CONFIG_PROP_CHECKSUM_ALGORITHMS))));

        final boolean failIfMissing = ConfigUtils.getBoolean(session, false, CONFIG_PROP_FAIL_IF_MISSING);
        final boolean record = ConfigUtils.getBoolean(session, false, CONFIG_PROP_RECORD);
        final boolean snapshots = ConfigUtils.getBoolean(session, false, CONFIG_PROP_SNAPSHOTS);

        for (ArtifactResult artifactResult : artifactResults) {
            if (artifactResult.getRequest().getArtifact().isSnapshot() && !snapshots) {
                continue;
            }
            if (inScope(session, artifactResult)) {
                if (record) {
                    recordArtifactChecksums(session, artifactResult, checksumAlgorithms);
                } else if (!validateArtifactChecksums(session, artifactResult, checksumAlgorithms, failIfMissing)) {
                    artifactResult.setArtifact(artifactResult.getArtifact().setPath(null)); // make it unresolved
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
                    ChecksumAlgorithmHelper.calculate(artifact.getPath(), checksumAlgorithmFactories);

            for (TrustedChecksumsSource trustedChecksumsSource : trustedChecksumsSources.values()) {
                TrustedChecksumsSource.Writer writer =
                        trustedChecksumsSource.getTrustedArtifactChecksumsWriter(session);
                if (writer != null) {
                    try {
                        writer.addTrustedArtifactChecksums(
                                artifact, artifactRepository, checksumAlgorithmFactories, calculatedChecksums);
                    } catch (IOException e) {
                        throw new UncheckedIOException(
                                "Could not write required checksums for " + artifact.getPath(), e);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not calculate required checksums for " + artifact.getPath(), e);
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
                    ChecksumAlgorithmHelper.calculate(artifact.getPath(), checksumAlgorithmFactories);

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
                        artifactResult.addException(
                                artifactRepository,
                                new ChecksumFailureException("Missing from " + trustedSourceName
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
                            artifactResult.addException(
                                    artifactRepository,
                                    new ChecksumFailureException("Artifact "
                                            + ArtifactIdUtils.toId(artifact) + " trusted checksum mismatch: "
                                            + trustedSourceName + "=" + trustedChecksum + "; calculated="
                                            + calculatedChecksum));
                            valid = false;
                        }
                    }
                }
            }

            if (!validated && failIfMissing) {
                artifactResult.addException(
                        artifactRepository,
                        new ChecksumFailureException(
                                "There are no enabled trusted checksums" + " source(s) to validate against."));
                valid = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return valid;
    }
}
