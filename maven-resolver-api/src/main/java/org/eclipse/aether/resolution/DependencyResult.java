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

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * The result of a dependency resolution request.
 *
 * @see RepositorySystem#resolveDependencies(org.eclipse.aether.RepositorySystemSession, DependencyRequest)
 */
public final class DependencyResult {

    private final DependencyRequest request;

    private DependencyNode root;

    private List<DependencyCycle> cycles;

    private List<Exception> collectExceptions;

    private List<DependencyNode> dependencyNodeResults;

    private List<ArtifactResult> artifactResults;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The resolution request, must not be {@code null}.
     */
    public DependencyResult(DependencyRequest request) {
        this.request = requireNonNull(request, "dependency request cannot be null");
        root = request.getRoot();
        cycles = Collections.emptyList();
        collectExceptions = Collections.emptyList();
        this.dependencyNodeResults = Collections.emptyList();
        artifactResults = Collections.emptyList();
    }

    /**
     * Gets the resolution request that was made.
     *
     * @return The resolution request, never {@code null}.
     */
    public DependencyRequest getRequest() {
        return request;
    }

    /**
     * Gets the root node of the resolved dependency graph. Note that this dependency graph might be
     * incomplete/unfinished in case of {@link #getCollectExceptions()} indicating errors during its calculation.
     *
     * @return The root node of the resolved dependency graph or {@code null} if none.
     */
    public DependencyNode getRoot() {
        return root;
    }

    /**
     * Sets the root node of the resolved dependency graph.
     *
     * @param root The root node of the resolved dependency graph, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setRoot(DependencyNode root) {
        this.root = root;
        return this;
    }

    /**
     * Gets the dependency cycles that were encountered while building the dependency graph. Note that dependency cycles
     * will only be reported here if the underlying request was created from a
     * {@link org.eclipse.aether.collection.CollectRequest CollectRequest}. If the underlying {@link DependencyRequest}
     * was created from an existing dependency graph, information about cycles will not be available in this result.
     *
     * @return The dependency cycles in the (raw) graph, never {@code null}.
     */
    public List<DependencyCycle> getCycles() {
        return cycles;
    }

    /**
     * Records the specified dependency cycles while building the dependency graph.
     *
     * @param cycles The dependency cycles to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setCycles(List<DependencyCycle> cycles) {
        if (cycles == null) {
            this.cycles = Collections.emptyList();
        } else {
            this.cycles = cycles;
        }
        return this;
    }

    /**
     * Gets the exceptions that occurred while building the dependency graph.
     *
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getCollectExceptions() {
        return collectExceptions;
    }

    /**
     * Records the specified exceptions while building the dependency graph.
     *
     * @param exceptions The exceptions to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setCollectExceptions(List<Exception> exceptions) {
        if (exceptions == null) {
            this.collectExceptions = Collections.emptyList();
        } else {
            this.collectExceptions = exceptions;
        }
        return this;
    }

    /**
     * Gets the resolution results for the dependency nodes that matched {@link DependencyRequest#getFilter()}.
     *
     * @return The resolution results for the dependency nodes, never {@code null}.
     * @since TBD
     */
    public List<DependencyNode> getDependencyNodeResults() {
        return dependencyNodeResults;
    }

    /**
     * Sets the resolution results for the dependency nodes that matched {@link DependencyRequest#getFilter()}.
     *
     * @param results The resolution results for the dependency nodes, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     * @since TBD
     */
    public DependencyResult setDependencyNodeResults(List<DependencyNode> results) {
        if (results == null) {
            this.dependencyNodeResults = Collections.emptyList();
        } else {
            this.dependencyNodeResults = results;
        }
        return this;
    }

    /**
     * Gets the resolution results for the dependency artifacts that matched {@link DependencyRequest#getFilter()}.
     *
     * @return The resolution results for the dependency artifacts, never {@code null}.
     */
    public List<ArtifactResult> getArtifactResults() {
        return artifactResults;
    }

    /**
     * Sets the resolution results for the artifacts that matched {@link DependencyRequest#getFilter()}.
     *
     * @param results The resolution results for the artifacts, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DependencyResult setArtifactResults(List<ArtifactResult> results) {
        if (results == null) {
            this.artifactResults = Collections.emptyList();
        } else {
            this.artifactResults = results;
        }
        return this;
    }

    @Override
    public String toString() {
        return String.valueOf(artifactResults);
    }
}
