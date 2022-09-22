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
 *
 * Note: the "trusted" meaning depends solely on implementation and the user using it. Resolver itself does nothing
 * for "trust" (like some crypto magic or what not). It all boils down that any source being used by implemention is
 * "trusted" by user or not, does user decide accept it as "trust" or not.
 *
 * @since TBD
 */
public interface TrustedChecksumsSource
{
    /**
     * May return the provided checksums (for given artifact transfer) from trusted source other than remote
     * repository, or {@code null}.
     *
     * @param session                    The repository system session.
     * @param artifact                   The artifact we want checksums for.
     * @param artifactRepository         The origin of the artifact (local, workspace, remote or {@code null}
     *                                   "hosted").
     * @param checksumAlgorithmFactories The checksum algorithms that are expected.
     * @return Map of expected checksums, or {@code null}.
     */
    Map<String, String> getTrustedArtifactChecksums( RepositorySystemSession session,
                                                     Artifact artifact,
                                                     ArtifactRepository artifactRepository,
                                                     List<ChecksumAlgorithmFactory> checksumAlgorithmFactories );
}
