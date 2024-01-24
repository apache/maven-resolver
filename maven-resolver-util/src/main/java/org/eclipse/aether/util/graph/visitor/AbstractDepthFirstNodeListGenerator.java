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
package org.eclipse.aether.util.graph.visitor;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * Abstract base class for depth first dependency tree traverses. Subclasses of this visitor will visit each node
 * exactly once regardless how many paths within the dependency graph lead to the node such that the resulting node
 * sequence is free of duplicates.
 * <p>
 * Actual vertex ordering (preorder, inorder, postorder) needs to be defined by subclasses through appropriate
 * implementations for {@link #visitEnter(org.eclipse.aether.graph.DependencyNode)} and
 * {@link #visitLeave(org.eclipse.aether.graph.DependencyNode)}.
 * <p>
 * Note: inorder vertex ordering is not provided out of the box, as resolver cannot partition (or does not know how to
 * partition) the node children into "left" and "right" partitions.
 * <p>
 * The newer classes {@link AbstractDependencyNodeConsumerVisitor} and {@link NodeListGenerator} offer
 * similar capabilities but are pluggable. Use of this class, while not deprecated, is discouraged. This class
 * is not used in Resolver and is kept only for backward compatibility reasons.
 *
 * @see AbstractDependencyNodeConsumerVisitor
 *
 * @deprecated See {@link AbstractDependencyNodeConsumerVisitor} that is more versatile.
 */
@Deprecated
abstract class AbstractDepthFirstNodeListGenerator implements DependencyVisitor {

    private final Map<DependencyNode, Object> visitedNodes;

    protected final List<DependencyNode> nodes;

    AbstractDepthFirstNodeListGenerator() {
        nodes = new ArrayList<>(128);
        visitedNodes = new IdentityHashMap<>(512);
    }

    /**
     * Gets the list of dependency nodes that was generated during the graph traversal.
     *
     * @return The list of dependency nodes, never {@code null}.
     */
    public List<DependencyNode> getNodes() {
        return nodes;
    }

    /**
     * Gets the dependencies seen during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved dependencies shall be included in the result or not.
     * @return The list of dependencies, never {@code null}.
     */
    public List<Dependency> getDependencies(boolean includeUnresolved) {
        return NodeListGenerator.getDependencies(getNodes(), includeUnresolved);
    }

    /**
     * Gets the artifacts associated with the list of dependency nodes generated during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved artifacts shall be included in the result or not.
     * @return The list of artifacts, never {@code null}.
     */
    public List<Artifact> getArtifacts(boolean includeUnresolved) {
        return NodeListGenerator.getArtifacts(getNodes(), includeUnresolved);
    }

    /**
     * Gets the files of resolved artifacts seen during the graph traversal.
     *
     * @return The list of artifact files, never {@code null}.
     */
    public List<File> getFiles() {
        return NodeListGenerator.getFiles(getNodes());
    }

    /**
     * Gets a class path by concatenating the artifact files of the visited dependency nodes. Nodes with unresolved
     * artifacts are automatically skipped.
     *
     * @return The class path, using the platform-specific path separator, never {@code null}.
     */
    public String getClassPath() {
        return NodeListGenerator.getClassPath(getNodes());
    }

    /**
     * Marks the specified node as being visited and determines whether the node has been visited before.
     *
     * @param node The node being visited, must not be {@code null}.
     * @return {@code true} if the node has not been visited before, {@code false} if the node was already visited.
     */
    protected boolean setVisited(DependencyNode node) {
        return visitedNodes.put(node, Boolean.TRUE) == null;
    }

    @Override
    public abstract boolean visitEnter(DependencyNode node);

    @Override
    public abstract boolean visitLeave(DependencyNode node);
}
