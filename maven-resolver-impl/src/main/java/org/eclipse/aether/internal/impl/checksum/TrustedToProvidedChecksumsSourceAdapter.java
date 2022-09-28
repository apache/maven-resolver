package org.eclipse.aether.internal.impl.checksum;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ProvidedChecksumsSource;

import static java.util.Objects.requireNonNull;

/**
 * Adapter that adapts {@link TrustedChecksumsSource} to {@link ProvidedChecksumsSource} used by connector. Hence, any
 * "trusted" source exist that is enabled, automatically becomes "provided" source as well.
 *
 * @since TBD
 */
@Singleton
@Named( TrustedToProvidedChecksumsSourceAdapter.NAME )
public final class TrustedToProvidedChecksumsSourceAdapter
        implements ProvidedChecksumsSource
{
    public static final String NAME = "trusted2provided";

    private final Map<String, TrustedChecksumsSource> trustedChecksumsSources;

    @Inject
    public TrustedToProvidedChecksumsSourceAdapter( Map<String, TrustedChecksumsSource> trustedChecksumsSources )
    {
        this.trustedChecksumsSources = requireNonNull( trustedChecksumsSources );
    }

    @Override
    public Map<String, String> getProvidedArtifactChecksums( RepositorySystemSession session,
                                                             ArtifactDownload transfer,
                                                             List<ChecksumAlgorithmFactory> checksumAlgorithmFactories )
    {
        Artifact artifact = transfer.getArtifact();
        for ( RemoteRepository remoteRepository : transfer.getRepositories() )
        {
            for ( TrustedChecksumsSource trustedChecksumsSource : trustedChecksumsSources.values() )
            {
                Map<String, String> trustedChecksums = trustedChecksumsSource
                        .getTrustedArtifactChecksums( session, artifact, remoteRepository, checksumAlgorithmFactories );
                if ( trustedChecksums != null && !trustedChecksums.isEmpty() )
                {
                    return trustedChecksums;
                }
            }
        }
        return null;
    }
}
