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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.aether.ConfigurationProperties;
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
import org.eclipse.aether.util.ConfigUtils;

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
    private static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "chainedLocalRepository.";

    /**
     * When using chained local repository, should be the artifact availability ignored in tail.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_IGNORE_TAIL_AVAILABILITY}
     */
    public static final String CONFIG_PROP_IGNORE_TAIL_AVAILABILITY = CONFIG_PROPS_PREFIX + "ignoreTailAvailability";

    public static final boolean DEFAULT_IGNORE_TAIL_AVAILABILITY = true;

    private final LocalRepositoryManager head;

    private final List<LocalRepositoryManager> tail;

    private final boolean ignoreTailAvailability;

    private final int installTarget;

    private final int cacheTarget;

    public ChainedLocalRepositoryManager(
            LocalRepositoryManager head, List<LocalRepositoryManager> tail, boolean ignoreTailAvailability) {
        this(head, tail, ignoreTailAvailability, 0, 0);
    }

    public ChainedLocalRepositoryManager(
            LocalRepositoryManager head, List<LocalRepositoryManager> tail, RepositorySystemSession session) {
        this(
                head,
                tail,
                ConfigUtils.getBoolean(session, DEFAULT_IGNORE_TAIL_AVAILABILITY, CONFIG_PROP_IGNORE_TAIL_AVAILABILITY),
                0,
                0);
    }

    /**
     * Warning: this is experimental feature of chained, is not recommended to be used/integrated into plain Maven.
     *
     * @param head the head LRM
     * @param tail the tail LRMs
     * @param ignoreTailAvailability whether tail availability should be ignored (usually you do want this)
     * @param installTarget the installation LRM index, integer from 0 to size of tail
     * @param cacheTarget the cache LRM index, integer from 0 to size of tail
     * @since 2.0.5
     */
    public ChainedLocalRepositoryManager(
            LocalRepositoryManager head,
            List<LocalRepositoryManager> tail,
            boolean ignoreTailAvailability,
            int installTarget,
            int cacheTarget) {
        this.head = requireNonNull(head, "head cannot be null");
        this.tail = requireNonNull(tail, "tail cannot be null");
        this.ignoreTailAvailability = ignoreTailAvailability;
        if (installTarget < 0 || installTarget > tail.size()) {
            throw new IllegalArgumentException("Illegal installTarget value");
        }
        this.installTarget = installTarget;
        if (cacheTarget < 0 || cacheTarget > tail.size()) {
            throw new IllegalArgumentException("Illegal cacheTarget value");
        }
        this.cacheTarget = cacheTarget;
    }

    @Override
    public LocalRepository getRepository() {
        return head.getRepository();
    }

    private LocalRepositoryManager getInstallTarget() {
        if (installTarget == 0) {
            return head;
        } else {
            return tail.get(installTarget - 1);
        }
    }

    private LocalRepositoryManager getCacheTarget() {
        if (cacheTarget == 0) {
            return head;
        } else {
            return tail.get(cacheTarget - 1);
        }
    }

    @Override
    public Path getAbsolutePathForLocalArtifact(Artifact artifact) {
        return getInstallTarget().getAbsolutePathForLocalArtifact(artifact);
    }

    @Override
    public Path getAbsolutePathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return getCacheTarget().getAbsolutePathForRemoteArtifact(artifact, repository, context);
    }

    @Override
    public Path getAbsolutePathForLocalMetadata(Metadata metadata) {
        return getInstallTarget().getAbsolutePathForLocalMetadata(metadata);
    }

    @Override
    public Path getAbsolutePathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return getCacheTarget().getAbsolutePathForRemoteMetadata(metadata, repository, context);
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return getInstallTarget().getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return getCacheTarget().getPathForRemoteArtifact(artifact, repository, context);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return getInstallTarget().getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return getCacheTarget().getPathForRemoteMetadata(metadata, repository, context);
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        LocalArtifactResult result = head.find(session, request);
        if (result.isAvailable()) {
            return result;
        }

        for (LocalRepositoryManager lrm : tail) {
            result = lrm.find(session, request);
            if (result.getPath() != null) {
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
        LocalRepositoryManager target;
        if (request.getRepository() != null) {
            artifactPath = getPathForRemoteArtifact(request.getArtifact(), request.getRepository(), "check");
            target = getCacheTarget();
        } else {
            artifactPath = getPathForLocalArtifact(request.getArtifact());
            target = getInstallTarget();
        }
        Path file = target.getRepository().getBasePath().resolve(artifactPath);
        if (Files.isRegularFile(file)) {
            target.add(session, request);
        }
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        LocalMetadataResult result = head.find(session, request);
        if (result.getPath() != null) {
            return result;
        }

        for (LocalRepositoryManager lrm : tail) {
            result = lrm.find(session, request);
            if (result.getPath() != null) {
                return result;
            }
        }
        return new LocalMetadataResult(request);
    }

    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        String metadataPath;
        LocalRepositoryManager target;
        if (request.getRepository() != null) {
            metadataPath = getPathForRemoteMetadata(request.getMetadata(), request.getRepository(), "check");
            target = getCacheTarget();
        } else {
            metadataPath = getPathForLocalMetadata(request.getMetadata());
            target = getInstallTarget();
        }

        Path file = target.getRepository().getBasePath().resolve(metadataPath);
        if (Files.isRegularFile(file)) {
            target.add(session, request);
        }
    }

    @Override
    public String toString() {
        return head.getRepository().toString()
                + tail.stream().map(LocalRepositoryManager::getRepository).collect(toList());
    }
}
