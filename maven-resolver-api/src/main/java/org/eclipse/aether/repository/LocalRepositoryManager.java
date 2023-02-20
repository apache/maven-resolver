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
package org.eclipse.aether.repository;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Manages access to a local repository.
 *
 * @see RepositorySystemSession#getLocalRepositoryManager()
 * @see org.eclipse.aether.RepositorySystem#newLocalRepositoryManager(RepositorySystemSession, LocalRepository)
 */
public interface LocalRepositoryManager {

    /**
     * Gets the description of the local repository being managed.
     *
     * @return The description of the local repository, never {@code null}.
     */
    LocalRepository getRepository();

    /**
     * Gets the relative path for a locally installed artifact. Note that the artifact need not actually exist yet at
     * the returned location, the path merely indicates where the artifact would eventually be stored. The path uses the
     * forward slash as directory separator regardless of the underlying file system.
     *
     * @param artifact The artifact for which to determine the path, must not be {@code null}.
     * @return The path, relative to the local repository's base directory.
     */
    String getPathForLocalArtifact(Artifact artifact);

    /**
     * Gets the relative path for an artifact cached from a remote repository. Note that the artifact need not actually
     * exist yet at the returned location, the path merely indicates where the artifact would eventually be stored. The
     * path uses the forward slash as directory separator regardless of the underlying file system.
     *
     * @param artifact The artifact for which to determine the path, must not be {@code null}.
     * @param repository The source repository of the artifact, must not be {@code null}.
     * @param context The resolution context in which the artifact is being requested, may be {@code null}.
     * @return The path, relative to the local repository's base directory.
     */
    String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context);

    /**
     * Gets the relative path for locally installed metadata. Note that the metadata need not actually exist yet at the
     * returned location, the path merely indicates where the metadata would eventually be stored. The path uses the
     * forward slash as directory separator regardless of the underlying file system.
     *
     * @param metadata The metadata for which to determine the path, must not be {@code null}.
     * @return The path, relative to the local repository's base directory.
     */
    String getPathForLocalMetadata(Metadata metadata);

    /**
     * Gets the relative path for metadata cached from a remote repository. Note that the metadata need not actually
     * exist yet at the returned location, the path merely indicates where the metadata would eventually be stored. The
     * path uses the forward slash as directory separator regardless of the underlying file system.
     *
     * @param metadata The metadata for which to determine the path, must not be {@code null}.
     * @param repository The source repository of the metadata, must not be {@code null}.
     * @param context The resolution context in which the metadata is being requested, may be {@code null}.
     * @return The path, relative to the local repository's base directory.
     */
    String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context);

    /**
     * Queries for the existence of an artifact in the local repository. The request could be satisfied by a locally
     * installed artifact or a previously downloaded artifact.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param request The artifact request, must not be {@code null}.
     * @return The result of the request, never {@code null}.
     */
    LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request);

    /**
     * Registers an installed or resolved artifact with the local repository. Note that artifact registration is merely
     * concerned about updating the local repository's internal state, not about actually installing the artifact or its
     * accompanying metadata.
     *
     * @param session The repository system session during which the registration is made, must not be {@code null}.
     * @param request The registration request, must not be {@code null}.
     */
    void add(RepositorySystemSession session, LocalArtifactRegistration request);

    /**
     * Queries for the existence of metadata in the local repository. The request could be satisfied by locally
     * installed or previously downloaded metadata.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param request The metadata request, must not be {@code null}.
     * @return The result of the request, never {@code null}.
     */
    LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request);

    /**
     * Registers installed or resolved metadata with the local repository. Note that metadata registration is merely
     * concerned about updating the local repository's internal state, not about actually installing the metadata.
     * However, this method MUST be called after the actual install to give the repository manager the opportunity to
     * inspect the added metadata.
     *
     * @param session The repository system session during which the registration is made, must not be {@code null}.
     * @param request The registration request, must not be {@code null}.
     */
    void add(RepositorySystemSession session, LocalMetadataRegistration request);
}
