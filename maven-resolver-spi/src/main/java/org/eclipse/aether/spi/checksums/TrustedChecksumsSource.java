package org.eclipse.aether.spi.checksums;

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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;

/**
 * Component able to provide (trusted) checksums for artifacts.
 * <p>
 * Note: the "trusted" meaning depends solely on implementation and the user using it. Resolver itself does nothing
 * for "trust" (like some crypto magic or what not). It all boils down that the source being used by implementation is
 * "trusted" by user or not.
 *
 * @since TBD
 */
public interface TrustedChecksumsSource
{
    /**
     * May return the trusted checksums (for given artifact) from trusted source, or {@code null} if not enabled.
     * Enabled trusted checksum source SHOULD return non-null (empty map) result, when it has no data for given
     * artifact. Empty map means in this case "no information", but how that case is interpreted depends on consumer
     * for trusted checksums.
     *
     * @param session                    The repository system session, never {@code null}.
     * @param artifact                   The artifact we want checksums for, never {@code null}.
     * @param artifactRepository         The origin repository: local, workspace, remote repository, never {@code null}.
     * @param checksumAlgorithmFactories The checksum algorithms that are expected, never {@code null}.
     * @return Map of expected checksums, or {@code null} if not enabled.
     */
    Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session,
                                                     Artifact artifact,
                                                     ArtifactRepository artifactRepository,
                                                     List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );

    /**
     * A writer that is able to write/add trusted checksums to this implementation. Should be treated as a resource
     * as underlying implementation may rely on being closed after not used anymore.
     */
    interface Writer extends Closeable
    {
        /**
         * Performs whatever implementation requires to "set" (write/add/append) given map of trusted checksums.
         * The passed in list of checksum algorithm factories and the map must have equal size and mapping must
         * contain all algorithm names in list.
         */
        void addTrustedArtifactChecksums( Artifact artifact,
                                          ArtifactRepository artifactRepository,
                                          List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
                                          Map<String, String> trustedArtifactChecksums ) throws IOException;
    }

    /**
     * Some trusted checksums sources may implement this optional method: ability to write/add checksums to them.
     * If source does not support this feature, method should return {@code null}.
     */
    Writer getTrustedArtifactChecksumsWriter( RepositorySystemSession session );
}
