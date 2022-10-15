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
package org.eclipse.aether.internal.test.util;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
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

/**
 * A simplistic local repository manager that uses a temporary base directory.
 */
public class TestLocalRepositoryManager implements LocalRepositoryManager {

    private LocalRepository localRepository;

    private final Set<Artifact> unavailableArtifacts = new HashSet<>();

    private final Set<Artifact> artifactRegistrations = new HashSet<>();

    private final Set<Metadata> metadataRegistrations = new HashSet<>();

    public TestLocalRepositoryManager() {
        try {
            localRepository = new LocalRepository(TestFileUtils.createTempDir("test-local-repo"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public LocalRepository getRepository() {
        return localRepository;
    }

    public String getPathForLocalArtifact(Artifact artifact) {
        requireNonNull(artifact, "artifact cannot be null");

        String artifactId = artifact.getArtifactId();
        String groupId = artifact.getGroupId();
        String extension = artifact.getExtension();
        String version = artifact.getVersion();
        String classifier = artifact.getClassifier();

        return String.format(
                "%s/%s/%s/%s-%s-%s%s.%s",
                groupId, artifactId, version, groupId, artifactId, version, classifier, extension);
    }

    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        requireNonNull(artifact, "artifact cannot be null");
        requireNonNull(repository, "repository cannot be null");

        return getPathForLocalArtifact(artifact);
    }

    public String getPathForLocalMetadata(Metadata metadata) {
        requireNonNull(metadata, "metadata cannot be null");

        String artifactId = metadata.getArtifactId();
        String groupId = metadata.getGroupId();
        String version = metadata.getVersion();
        return String.format("%s/%s/%s/%s-%s-%s.xml", groupId, artifactId, version, groupId, artifactId, version);
    }

    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        requireNonNull(metadata, "metadata cannot be null");
        requireNonNull(repository, "repository cannot be null");

        return getPathForLocalMetadata(metadata);
    }

    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        Artifact artifact = request.getArtifact();

        LocalArtifactResult result = new LocalArtifactResult(request);
        File file = new File(localRepository.getBasedir(), getPathForLocalArtifact(artifact));
        result.setFile(file.isFile() ? file : null);
        result.setAvailable(file.isFile() && !unavailableArtifacts.contains(artifact));

        return result;
    }

    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        artifactRegistrations.add(request.getArtifact());
    }

    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        Metadata metadata = request.getMetadata();

        LocalMetadataResult result = new LocalMetadataResult(request);
        File file = new File(localRepository.getBasedir(), getPathForLocalMetadata(metadata));
        result.setFile(file.isFile() ? file : null);

        return result;
    }

    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");

        metadataRegistrations.add(request.getMetadata());
    }

    public Set<Artifact> getArtifactRegistration() {
        return artifactRegistrations;
    }

    public Set<Metadata> getMetadataRegistration() {
        return metadataRegistrations;
    }

    public void setArtifactAvailability(Artifact artifact, boolean available) {
        if (available) {
            unavailableArtifacts.remove(artifact);
        } else {
            unavailableArtifacts.add(artifact);
        }
    }
}
