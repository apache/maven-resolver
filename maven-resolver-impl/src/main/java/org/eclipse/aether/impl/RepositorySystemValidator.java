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

import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRequest;

/**
 * Validator used by {@link org.eclipse.aether.RepositorySystem}. All method throw {@link IllegalArgumentException}.
 */
public interface RepositorySystemValidator {
    void validateVersionRequest(RepositorySystemSession session, VersionRequest request);

    void validateVersionRangeRequest(RepositorySystemSession session, VersionRangeRequest request);

    void validateArtifactDescriptorRequest(RepositorySystemSession session, ArtifactDescriptorRequest request);

    void validateArtifactRequests(RepositorySystemSession session, Collection<? extends ArtifactRequest> requests);

    void validateMetadataRequests(RepositorySystemSession session, Collection<? extends MetadataRequest> requests);

    void validateCollectRequest(RepositorySystemSession session, CollectRequest request);

    void validateDependencyRequest(RepositorySystemSession session, DependencyRequest request);

    void validateInstallRequest(RepositorySystemSession session, InstallRequest request);

    void validateDeployRequest(RepositorySystemSession session, DeployRequest request);

    void validateLocalRepositories(RepositorySystemSession session, Collection<LocalRepository> repositories);

    void validateRemoteRepositories(RepositorySystemSession session, Collection<RemoteRepository> repositories);
}
