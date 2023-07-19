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
package org.eclipse.aether.spi.checksums;

import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;

/**
 * Component able to provide (expected) checksums to connector beforehand the download happens. Checksum provided by
 * this component are of kind {@link ChecksumPolicy.ChecksumKind#PROVIDED}. Resolver by default provides one
 * implementation: an adapter, that makes {@link TrustedChecksumsSource} into {@link ProvidedChecksumsSource}. Users
 * are encouraged to rely on this adapter, and do not create their own implementations.
 *
 * @since 1.9.14
 */
public interface ProvidedChecksumsSource {
    /**
     * May return the provided checksums (for given artifact transfer) from source other than remote repository, or
     * {@code null} if it have no checksums available for given transfer. Provided checksums are "opt-in" for
     * transfer, in a way IF they are available upfront, they will be enforced according to checksum policy
     * in effect. Otherwise, provided checksum verification is completely left out.
     * <p>
     * For enabled provided checksum source is completely acceptable to return {@code null} values, as that carries
     * the meaning "nothing to add here", as there are no checksums to be provided upfront transfer. Semantically, this
     * is equivalent to returning empty map, but signals the intent better.
     *
     * @param session                    The current session.
     * @param transfer                   The transfer that is about to be executed.
     * @param remoteRepository           The remote repository connector is about to contact.
     * @param checksumAlgorithmFactories The checksum algorithms that are expected.
     * @return Map of expected checksums, or {@code null}.
     */
    Map<String, String> getProvidedArtifactChecksums(
            RepositorySystemSession session,
            ArtifactDownload transfer,
            RemoteRepository remoteRepository,
            List<ChecksumAlgorithmFactory> checksumAlgorithmFactories);
}
