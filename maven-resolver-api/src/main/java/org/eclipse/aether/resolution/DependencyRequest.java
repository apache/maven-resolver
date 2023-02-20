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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A request to resolve transitive dependencies. This request can either be supplied with a {@link CollectRequest} to
 * calculate the transitive dependencies or with an already resolved dependency graph.
 *
 * @see RepositorySystem#resolveDependencies(org.eclipse.aether.RepositorySystemSession, DependencyRequest)
 * @see Artifact#getFile()
 */
public final class DependencyRequest {

    private DependencyNode root;

    private CollectRequest collectRequest;

    private DependencyFilter filter;

    private RequestTrace trace;

    /**
     * Creates an uninitialized request. Note that either {@link #setRoot(DependencyNode)} or
     * {@link #setCollectRequest(CollectRequest)} must eventually be called to create a valid request.
     */
    public DependencyRequest() {
        // enables default constructor
    }

    /**
     * Creates a request for the specified dependency graph and with the given resolution filter.
     *
     * @param node The root node of the dependency graph whose artifacts should be resolved, may be {@code null}.
     * @param filter The resolution filter to use, may be {@code null}.
     */
    public DependencyRequest(DependencyNode node, DependencyFilter filter) {
        setRoot(node);
        setFilter(filter);
    }

    /**
     * Creates a request for the specified collect request and with the given resolution filter.
     *
     * @param request The collect request used to calculate the dependency graph whose artifacts should be resolved, may
     *            be {@code null}.
     * @param filter The resolution filter to use, may be {@code null}.
     */
    public DependencyRequest(CollectRequest request, DependencyFilter filter) {
        setCollectRequest(request);
        setFilter(filter);
    }

    /**
     * Gets the root node of the dependency graph whose artifacts should be resolved.
     *
     * @return The root node of the dependency graph or {@code null} if none.
     */
    public DependencyNode getRoot() {
        return root;
    }

    /**
     * Sets the root node of the dependency graph whose artifacts should be resolved. When this request is processed,
     * the nodes of the given dependency graph will be updated to refer to the resolved artifacts. Eventually, either
     * {@link #setRoot(DependencyNode)} or {@link #setCollectRequest(CollectRequest)} must be called to create a valid
     * request.
     *
     * @param root The root node of the dependency graph, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DependencyRequest setRoot(DependencyNode root) {
        this.root = root;
        return this;
    }

    /**
     * Gets the collect request used to calculate the dependency graph whose artifacts should be resolved.
     *
     * @return The collect request or {@code null} if none.
     */
    public CollectRequest getCollectRequest() {
        return collectRequest;
    }

    /**
     * Sets the collect request used to calculate the dependency graph whose artifacts should be resolved. Eventually,
     * either {@link #setRoot(DependencyNode)} or {@link #setCollectRequest(CollectRequest)} must be called to create a
     * valid request. If this request is supplied with a dependency node via {@link #setRoot(DependencyNode)}, the
     * collect request is ignored.
     *
     * @param collectRequest The collect request, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DependencyRequest setCollectRequest(CollectRequest collectRequest) {
        this.collectRequest = collectRequest;
        return this;
    }

    /**
     * Gets the resolution filter used to select which artifacts of the dependency graph should be resolved.
     *
     * @return The resolution filter or {@code null} to resolve all artifacts of the dependency graph.
     */
    public DependencyFilter getFilter() {
        return filter;
    }

    /**
     * Sets the resolution filter used to select which artifacts of the dependency graph should be resolved. For
     * example, use this filter to restrict resolution to dependencies of a certain scope.
     *
     * @param filter The resolution filter, may be {@code null} to resolve all artifacts of the dependency graph.
     * @return This request for chaining, never {@code null}.
     */
    public DependencyRequest setFilter(DependencyFilter filter) {
        this.filter = filter;
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
    public DependencyRequest setTrace(RequestTrace trace) {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString() {
        if (root != null) {
            return String.valueOf(root);
        }
        return String.valueOf(collectRequest);
    }
}
