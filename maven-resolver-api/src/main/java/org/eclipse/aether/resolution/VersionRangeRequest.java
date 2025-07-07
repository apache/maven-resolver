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
package org.eclipse.aether.resolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to resolve a version range.
 *
 * @see org.eclipse.aether.RepositorySystem#resolveVersionRange(org.eclipse.aether.RepositorySystemSession,
 *  VersionRangeRequest)
 */
public final class VersionRangeRequest {

    private Artifact artifact;

    private List<RemoteRepository> repositories = Collections.emptyList();

    private Metadata.Nature nature = Metadata.Nature.RELEASE_OR_SNAPSHOT;

    private String context = "";

    private RequestTrace trace;

    /**
     * Creates an uninitialized request.
     */
    public VersionRangeRequest() {
        // enables default constructor
    }

    /**
     * Creates a request with the specified properties.
     *
     * @param artifact The artifact whose version range should be resolved, may be {@code null}.
     * @param repositories The repositories to resolve the version from, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public VersionRangeRequest(Artifact artifact, List<RemoteRepository> repositories, String context) {
        setArtifact(artifact);
        setRepositories(repositories);
        setRequestContext(context);
    }

    /**
     * Creates a request with the specified properties.
     *
     * @param artifact The artifact whose version range should be resolved, may be {@code null}.
     * @param repositories The repositories to resolve the version from, may be {@code null}.
     * @param nature The nature of metadata to use for resolving the version from, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public VersionRangeRequest(
            Artifact artifact, List<RemoteRepository> repositories, Metadata.Nature nature, String context) {
        setArtifact(artifact);
        setRepositories(repositories);
        setNature(nature);
        setRequestContext(context);
    }

    /**
     * Gets the artifact whose version range shall be resolved.
     *
     * @return The artifact or {@code null} if not set.
     */
    public Artifact getArtifact() {
        return artifact;
    }

    /**
     * Sets the artifact whose version range shall be resolved.
     *
     * @param artifact The artifact, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setArtifact(Artifact artifact) {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the repositories to resolve the version range from.
     *
     * @return The repositories, never {@code null}.
     */
    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    /**
     * Sets the repositories to resolve the version range from.
     *
     * @param repositories The repositories, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setRepositories(List<RemoteRepository> repositories) {
        if (repositories == null) {
            this.repositories = Collections.emptyList();
        } else {
            this.repositories = repositories;
        }
        return this;
    }

    /**
     * Adds the specified repository for the resolution.
     *
     * @param repository The repository to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest addRepository(RemoteRepository repository) {
        if (repository != null) {
            if (this.repositories.isEmpty()) {
                this.repositories = new ArrayList<>();
            }
            this.repositories.add(repository);
        }
        return this;
    }

    /**
     * The nature of metadata to use for resolving the version from, never {@code null}.
     *
     * @return The nature, never {@code null}.
     * @since 1.9.25
     */
    public Metadata.Nature getNature() {
        return nature;
    }

    /**
     * Sets the nature of metadata to use for resolving the version from
     *
     * @param nature The nature, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     * @since 1.9.25
     */
    public VersionRangeRequest setNature(Metadata.Nature nature) {
        if (nature == null) {
            this.nature = Metadata.Nature.RELEASE_OR_SNAPSHOT;
        } else {
            this.nature = nature;
        }
        return this;
    }

    /**
     * Gets the context in which this request is made.
     *
     * @return The context, never {@code null}.
     */
    public String getRequestContext() {
        return context;
    }

    /**
     * Sets the context in which this request is made.
     *
     * @param context The context, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setRequestContext(String context) {
        this.context = (context != null) ? context.intern() : "";
        return this;
    }

    /**
     * Gets the trace information that describes the higher level request/operation in which this request is issued.
     *
     * @return The trace information about the higher level operation or {@code null} if none.
     */
    public RequestTrace getTrace() {
        return trace;
    }

    /**
     * Sets the trace information that describes the higher level request/operation in which this request is issued.
     *
     * @param trace The trace information about the higher level operation, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setTrace(RequestTrace trace) {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString() {
        return getArtifact() + " < " + getRepositories();
    }
}
