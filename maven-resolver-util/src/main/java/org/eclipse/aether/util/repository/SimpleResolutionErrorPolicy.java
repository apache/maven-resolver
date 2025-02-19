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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicyRequest;

import static java.util.Objects.requireNonNull;

/**
 * A resolution error policy that allows to control caching for artifacts and metadata at a global level.
 */
public final class SimpleResolutionErrorPolicy implements ResolutionErrorPolicy {

    private final int artifactPolicy;

    private final int metadataPolicy;

    /**
     * Creates a new error policy with the specified behavior for both artifacts and metadata.
     *
     * @param cacheNotFound {@code true} to enable caching of missing items, {@code false} to disable it.
     * @param cacheTransferErrors {@code true} to enable chaching of transfer errors, {@code false} to disable it.
     */
    public SimpleResolutionErrorPolicy(boolean cacheNotFound, boolean cacheTransferErrors) {
        this((cacheNotFound ? CACHE_NOT_FOUND : 0) | (cacheTransferErrors ? CACHE_TRANSFER_ERROR : 0));
    }

    /**
     * Creates a new error policy with the specified bit mask for both artifacts and metadata.
     *
     * @param policy The bit mask describing the policy for artifacts and metadata.
     */
    public SimpleResolutionErrorPolicy(int policy) {
        this(policy, policy);
    }

    /**
     * Creates a new error policy with the specified bit masks for artifacts and metadata.
     *
     * @param artifactPolicy The bit mask describing the policy for artifacts.
     * @param metadataPolicy The bit mask describing the policy for metadata.
     */
    public SimpleResolutionErrorPolicy(int artifactPolicy, int metadataPolicy) {
        this.artifactPolicy = artifactPolicy;
        this.metadataPolicy = metadataPolicy;
    }

    public int getArtifactPolicy(RepositorySystemSession session, ResolutionErrorPolicyRequest<Artifact> request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        return artifactPolicy;
    }

    public int getMetadataPolicy(RepositorySystemSession session, ResolutionErrorPolicyRequest<Metadata> request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        return metadataPolicy;
    }
}
