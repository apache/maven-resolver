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

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that adapts {@link TrustedChecksumsSource} to {@link ProvidedChecksumsSource} used by connector. Hence, any
 * "trusted" source exist that is enabled, automatically becomes "provided" source as well.
 *
 * @since 1.9.0
 */
@Singleton
@Named(TrustedToProvidedChecksumsSourceAdapter.NAME)
public final class TrustedToProvidedChecksumsSourceAdapter implements ProvidedChecksumsSource {
    public static final String NAME = "trusted2provided";

    private final Map<String, TrustedChecksumsSource> trustedChecksumsSources;

    @Inject
    public TrustedToProvidedChecksumsSourceAdapter(Map<String, TrustedChecksumsSource> trustedChecksumsSources) {
        this.trustedChecksumsSources = requireNonNull(trustedChecksumsSources);
    }

    @Override
    public Map<String, String> getProvidedArtifactChecksums(
            RepositorySystemSession session,
            ArtifactDownload transfer,
            RemoteRepository repository,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
        Artifact artifact = transfer.getArtifact();
        Map<String, String> trustedChecksums;
        // check for connector repository
        for (TrustedChecksumsSource trustedChecksumsSource : trustedChecksumsSources.values()) {
            trustedChecksums = trustedChecksumsSource.getTrustedArtifactChecksums(
                    session, artifact, repository, checksumAlgorithmFactories);
            if (trustedChecksums != null && !trustedChecksums.isEmpty()) {
                return trustedChecksums;
            }
        }
        // if repo above is "mirrorOf", this one kicks in
        if (!transfer.getRepositories().isEmpty()) {
            for (RemoteRepository remoteRepository : transfer.getRepositories()) {
                for (TrustedChecksumsSource trustedChecksumsSource : trustedChecksumsSources.values()) {
                    trustedChecksums = trustedChecksumsSource.getTrustedArtifactChecksums(
                            session, artifact, remoteRepository, checksumAlgorithmFactories);
                    if (trustedChecksums != null && !trustedChecksums.isEmpty()) {
                        return trustedChecksums;
                    }
                }
            }
        }
        return null;
    }
}
