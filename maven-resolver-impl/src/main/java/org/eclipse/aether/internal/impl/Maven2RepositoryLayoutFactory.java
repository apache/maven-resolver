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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.artifact.ArtifactPredicate;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Provides a Maven-2 repository layout for repositories with content type {@code "default"}.
 */
@Singleton
@Named(Maven2RepositoryLayoutFactory.NAME)
public final class Maven2RepositoryLayoutFactory implements RepositoryLayoutFactory {
    public static final String NAME = "maven2";

    private static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_LAYOUT + NAME + ".";

    /**
     * Comma-separated list of checksum algorithms with which checksums are validated (downloaded) and generated
     * (uploaded) with this layout. Resolver by default supports following algorithms: MD5, SHA-1, SHA-256 and
     * SHA-512. New algorithms can be added by implementing ChecksumAlgorithmFactory component.
     *
     * @since 1.8.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_CHECKSUMS_ALGORITHMS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_CHECKSUMS_ALGORITHMS = CONFIG_PROPS_PREFIX + "checksumAlgorithms";

    public static final String DEFAULT_CHECKSUMS_ALGORITHMS = "SHA-1,MD5";

    private float priority;

    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;

    private final ArtifactPredicateFactory artifactPredicateFactory;

    public float getPriority() {
        return priority;
    }

    @Inject
    public Maven2RepositoryLayoutFactory(
            ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector,
            ArtifactPredicateFactory artifactPredicateFactory) {
        this.checksumAlgorithmFactorySelector = requireNonNull(checksumAlgorithmFactorySelector);
        this.artifactPredicateFactory = requireNonNull(artifactPredicateFactory);
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public Maven2RepositoryLayoutFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }

    public RepositoryLayout newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryLayoutException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        if (!"default".equals(repository.getContentType())) {
            throw new NoRepositoryLayoutException(repository);
        }

        List<ChecksumAlgorithmFactory> checksumsAlgorithms = checksumAlgorithmFactorySelector.selectList(
                ConfigUtils.parseCommaSeparatedUniqueNames(ConfigUtils.getString(
                        session,
                        DEFAULT_CHECKSUMS_ALGORITHMS,
                        CONFIG_PROP_CHECKSUMS_ALGORITHMS + "." + repository.getId(),
                        CONFIG_PROP_CHECKSUMS_ALGORITHMS)));

        return new Maven2RepositoryLayout(checksumsAlgorithms, artifactPredicateFactory.newInstance(session));
    }

    private static class Maven2RepositoryLayout implements RepositoryLayout {
        private final List<ChecksumAlgorithmFactory> configuredChecksumAlgorithms;
        private final ArtifactPredicate artifactPredicate;

        private Maven2RepositoryLayout(
                List<ChecksumAlgorithmFactory> configuredChecksumAlgorithms, ArtifactPredicate artifactPredicate) {
            this.configuredChecksumAlgorithms = Collections.unmodifiableList(configuredChecksumAlgorithms);
            this.artifactPredicate = requireNonNull(artifactPredicate);
        }

        private URI toUri(String path) {
            try {
                return new URI(null, null, path, null);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories() {
            return configuredChecksumAlgorithms;
        }

        @Override
        public boolean hasChecksums(Artifact artifact) {
            return !artifactPredicate.isWithoutChecksum(artifact);
        }

        @Override
        public URI getLocation(Artifact artifact, boolean upload) {
            StringBuilder path = new StringBuilder(128);

            path.append(artifact.getGroupId().replace('.', '/')).append('/');

            path.append(artifact.getArtifactId()).append('/');

            path.append(artifact.getBaseVersion()).append('/');

            path.append(artifact.getArtifactId()).append('-').append(artifact.getVersion());

            if (!artifact.getClassifier().isEmpty()) {
                path.append('-').append(artifact.getClassifier());
            }

            if (!artifact.getExtension().isEmpty()) {
                path.append('.').append(artifact.getExtension());
            }

            return toUri(path.toString());
        }

        @Override
        public URI getLocation(Metadata metadata, boolean upload) {
            StringBuilder path = new StringBuilder(128);

            if (!metadata.getGroupId().isEmpty()) {
                path.append(metadata.getGroupId().replace('.', '/')).append('/');

                if (!metadata.getArtifactId().isEmpty()) {
                    path.append(metadata.getArtifactId()).append('/');

                    if (!metadata.getVersion().isEmpty()) {
                        path.append(metadata.getVersion()).append('/');
                    }
                }
            }

            path.append(metadata.getType());

            return toUri(path.toString());
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Artifact artifact, boolean upload, URI location) {
            if (artifactPredicate.isWithoutChecksum(artifact) || artifactPredicate.isChecksum(artifact)) {
                return Collections.emptyList();
            }
            return getChecksumLocations(location);
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Metadata metadata, boolean upload, URI location) {
            return getChecksumLocations(location);
        }

        private List<ChecksumLocation> getChecksumLocations(URI location) {
            List<ChecksumLocation> checksumLocations = new ArrayList<>(configuredChecksumAlgorithms.size());
            for (ChecksumAlgorithmFactory checksumAlgorithmFactory : configuredChecksumAlgorithms) {
                checksumLocations.add(ChecksumLocation.forLocation(location, checksumAlgorithmFactory));
            }
            return checksumLocations;
        }
    }
}
