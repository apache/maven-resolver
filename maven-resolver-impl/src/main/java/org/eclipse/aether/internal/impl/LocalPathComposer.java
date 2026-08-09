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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Composes {@link Artifact} and {@link Metadata} relative paths to be used in
 * {@link org.eclipse.aether.repository.LocalRepositoryManager}.
 *
 * @since 1.8.1
 */
public interface LocalPathComposer {
    /**
     * Gets the relative path for a locally installed (local=true) or remotely cached (local=false) artifact.
     *
     * @param artifact The artifact for which to determine the path, must not be {@code null}.
     * @param local    {@code true} if artifact is locally installed or {@code false} if artifact is remotely cached.
     * @return A relative path representing artifact path.
     */
    String getPathForArtifact(Artifact artifact, boolean local);

    /**
     * Gets the relative path for locally installed (repositoryKey=local) or remotely cached metadata. The
     * {@code repositoryKey} should be used at caller discretion, it merely denotes the origin of the metadata, while
     * value "local" usually means local origin, but again, this is not a must or enforced, just how things happened
     * so far.
     *
     * @param metadata      The metadata for which to determine the path, must not be {@code null}.
     * @param repositoryKey The repository key, never {@code null}.
     * @return A relative path representing metadata path.
     */
    String getPathForMetadata(Metadata metadata, String repositoryKey);
}
