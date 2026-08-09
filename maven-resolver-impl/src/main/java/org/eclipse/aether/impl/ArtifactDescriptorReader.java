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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

/**
 * Provides information about an artifact that is relevant to transitive dependency resolution.
 * Each artifact is expected to have an accompanying <em>artifact descriptor</em> that among others lists
 * the direct dependencies of the artifact.
 *
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface ArtifactDescriptorReader {
    /**
     * Gets information about an artifact like its direct dependencies and potential relocations. Implementations must
     * respect the {@link RepositorySystemSession#getArtifactDescriptorPolicy() artifact descriptor policy} of the
     * session when dealing with certain error cases.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The descriptor request, must not be {@code null}
     * @return The descriptor result, never {@code null}.
     * @throws ArtifactDescriptorException If the artifact descriptor could not be read.
     * @see RepositorySystem#readArtifactDescriptor(RepositorySystemSession, ArtifactDescriptorRequest)
     */
    ArtifactDescriptorResult readArtifactDescriptor(RepositorySystemSession session, ArtifactDescriptorRequest request)
            throws ArtifactDescriptorException;
}
