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
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Composes path prefixes for {@link EnhancedLocalRepositoryManager}.
 *
 * @since 1.8.1
 */
public interface LocalPathPrefixComposer {
    /**
     * Gets the path prefix for a locally installed artifact.
     *
     * @param artifact The artifact for which to determine the prefix, must not be {@code null}.
     * @return The prefix, may be {@code null} (note: {@code null}s and empty strings are treated equally).
     */
    String getPathPrefixForLocalArtifact(Artifact artifact);

    /**
     * Gets the path prefix for an artifact cached from a remote repository.
     *
     * @param artifact      The artifact for which to determine the prefix, must not be {@code null}.
     * @param repository    The remote repository, never {@code null}.
     * @return The prefix, may be {@code null} (note: {@code null}s and empty strings are treated equally).
     */
    String getPathPrefixForRemoteArtifact(Artifact artifact, RemoteRepository repository);

    /**
     * Gets the path prefix for locally installed metadata.
     *
     * @param metadata The metadata for which to determine the prefix, must not be {@code null}.
     * @return The prefix, may be {@code null} (note: {@code null}s and empty strings are treated equally).
     */
    String getPathPrefixForLocalMetadata(Metadata metadata);

    /**
     * Gets the path prefix for metadata cached from a remote repository.
     *
     * @param metadata      The metadata for which to determine the prefix, must not be {@code null}.
     * @param repository    The remote repository, never {@code null}.
     * @return The prefix, may be {@code null} (note: {@code null}s and empty strings are treated equally).
     */
    String getPathPrefixForRemoteMetadata(Metadata metadata, RemoteRepository repository);
}
