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
package org.eclipse.aether.util.repository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A local repository manager that chains multiple local repository managers: it directs all the write operations
 * to chain head, while uses tail for {@link #find(RepositorySystemSession, LocalArtifactRequest)} and
 * {@link #find(RepositorySystemSession, LocalMetadataRequest)} methods only. Hence, tail is used in resolving
 * metadata and artifacts with or without (configurable) artifact availability tracking.
 * <p>
 * Implementation represents itself using the head local repository manager.
 *
 * @since 1.9.2
 */
public final class ChainedLocalRepositoryManager implements LocalRepositoryManager {
    private final LocalRepositoryManager head;

    private final List<LocalRepositoryManager> tail;

    private final boolean ignoreTailAvailability;

    public ChainedLocalRepositoryManager(
            LocalRepositoryManager head, List<LocalRepositoryManager> tail, boolean ignoreTailAvailability) {
        this.head = requireNonNull(head, "head cannot be null");
        this.tail = requireNonNull(tail, "tail cannot be null");
        this.ignoreTailAvailability = ignoreTailAvailability;
    }

    @Override
    public LocalRepository getRepository() {
        return head.getRepository();
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return head.getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return head.getPathForRemoteArtifact(artifact, repository, context);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return head.getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return head.getPathForRemoteMetadata(metadata, repository, context);
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        LocalArtifactResult result = head.find(session, request);
        if (result.isAvailable()) {
            return result;
        }

        for (LocalRepositoryManager lrm : tail) {
            result = lrm.find(session, request);
            if (result.getFile() != null) {
                if (ignoreTailAvailability) {
                    result.setAvailable(true);
                    return result;
                } else if (result.isAvailable()) {
                    return result;
                }
            }
        }
        return new LocalArtifactResult(request);
    }

    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        String artifactPath;
        if (request.getRepository() != null) {
            artifactPath = getPathForRemoteArtifact(request.getArtifact(), request.getRepository(), "check");
        } else {
            artifactPath = getPathForLocalArtifact(request.getArtifact());
        }

        Path file = new File(head.getRepository().getBasedir(), artifactPath).toPath();
        if (Files.isRegularFile(file)) {
            head.add(session, request);
        }
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        LocalMetadataResult result = head.find(session, request);
        if (result.getFile() != null) {
            return result;
        }

        for (LocalRepositoryManager lrm : tail) {
            result = lrm.find(session, request);
            if (result.getFile() != null) {
                return result;
            }
        }
        return new LocalMetadataResult(request);
    }

    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        String metadataPath;
        if (request.getRepository() != null) {
            metadataPath = getPathForRemoteMetadata(request.getMetadata(), request.getRepository(), "check");
        } else {
            metadataPath = getPathForLocalMetadata(request.getMetadata());
        }

        Path file = new File(head.getRepository().getBasedir(), metadataPath).toPath();
        if (Files.isRegularFile(file)) {
            head.add(session, request);
        }
    }

    @Override
    public String toString() {
        return head.getRepository().toString()
                + tail.stream().map(LocalRepositoryManager::getRepository).collect(toList());
    }
}
