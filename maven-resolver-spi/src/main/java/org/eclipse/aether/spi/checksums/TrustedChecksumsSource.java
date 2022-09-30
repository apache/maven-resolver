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
     * May return the trusted checksums (for given artifact) from trusted source, or {@code null}.
     *
     * @param session                    The repository system session, never {@code null}.
     * @param artifact                   The artifact we want checksums for, never {@code null}.
     * @param artifactRepository         The origin repository: local, workspace, remote repository, never {@code null}.
     * @param checksumAlgorithmFactories The checksum algorithms that are expected, never {@code null}.
     * @return Map of expected checksums, or {@code null}.
     */
    Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session,
                                                     Artifact artifact,
                                                     ArtifactRepository artifactRepository,
                                                     List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );
}
