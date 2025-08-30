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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.StringDigestUtil;

import static java.util.Objects.requireNonNull;

/**
 * A local repository manager that realizes the classical Maven 2.0 local repository.
 */
class SimpleLocalRepositoryManager implements LocalRepositoryManager {

    private final LocalRepository repository;

    private final LocalPathComposer localPathComposer;

    private final Function<RemoteRepository, String> remoteRepositorySafeId;

    SimpleLocalRepositoryManager(
            Path basePath,
            String type,
            LocalPathComposer localPathComposer,
            Function<RemoteRepository, String> remoteRepositorySafeId) {
        requireNonNull(basePath, "base directory cannot be null");
        repository = new LocalRepository(basePath.toAbsolutePath(), type);
        this.localPathComposer = requireNonNull(localPathComposer);
        this.remoteRepositorySafeId = requireNonNull(remoteRepositorySafeId);
    }

    @Override
    public LocalRepository getRepository() {
        return repository;
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        requireNonNull(artifact, "artifact cannot be null");
        return localPathComposer.getPathForArtifact(artifact, true);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        requireNonNull(artifact, "artifact cannot be null");
        requireNonNull(repository, "repository cannot be null");
        return localPathComposer.getPathForArtifact(artifact, false);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        requireNonNull(metadata, "metadata cannot be null");
        return localPathComposer.getPathForMetadata(metadata, "local");
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        requireNonNull(metadata, "metadata cannot be null");
        requireNonNull(repository, "repository cannot be null");
        return localPathComposer.getPathForMetadata(metadata, getRepositoryKey(repository, context));
    }

    /**
     * Returns {@link RemoteRepository#getId()}, unless {@link RemoteRepository#isRepositoryManager()} returns
     * {@code true}, in which case this method creates unique identifier based on ID and current configuration
     * of the remote repository (as it may change).
     */
    protected String getRepositoryKey(RemoteRepository repository, String context) {
        String key;

        if (repository.isRepositoryManager()) {
            // repository serves dynamic contents, take request parameters into account for key

            StringBuilder buffer = new StringBuilder(128);

            buffer.append(repository.getId());

            buffer.append('-');

            SortedSet<String> subKeys = new TreeSet<>();
            for (RemoteRepository mirroredRepo : repository.getMirroredRepositories()) {
                subKeys.add(mirroredRepo.getId());
            }

            StringDigestUtil sha1 = StringDigestUtil.sha1();
            sha1.update(context);
            for (String subKey : subKeys) {
                sha1.update(subKey);
            }
            buffer.append(sha1.digest());

            key = buffer.toString();
        } else {
            key = remoteRepositorySafeId.apply(repository);
        }

        return key;
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        Artifact artifact = request.getArtifact();
        LocalArtifactResult result = new LocalArtifactResult(request);

        Path filePath;

        // Local repository CANNOT have timestamped installed, they are created only during deploy
        if (Objects.equals(artifact.getVersion(), artifact.getBaseVersion())) {
            filePath = getAbsolutePathForLocalArtifact(artifact);
            if (Files.isRegularFile(filePath)) {
                result.setPath(filePath);
                result.setAvailable(true);
            }
        }

        if (!result.isAvailable()) {
            for (RemoteRepository repository : request.getRepositories()) {
                filePath = getAbsolutePathForRemoteArtifact(artifact, repository, request.getContext());
                if (Files.isRegularFile(filePath)) {
                    result.setPath(filePath);
                    result.setAvailable(true);
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        // noop
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        LocalMetadataResult result = new LocalMetadataResult(request);

        String path;

        Metadata metadata = request.getMetadata();
        String context = request.getContext();
        RemoteRepository remote = request.getRepository();

        if (remote != null) {
            path = getPathForRemoteMetadata(metadata, remote, context);
        } else {
            path = getPathForLocalMetadata(metadata);
        }

        Path filePath = getRepository().getBasePath().resolve(path);
        if (Files.isRegularFile(filePath)) {
            result.setPath(filePath);
        }

        return result;
    }

    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        // noop
    }

    @Override
    public String toString() {
        return String.valueOf(getRepository());
    }
}
