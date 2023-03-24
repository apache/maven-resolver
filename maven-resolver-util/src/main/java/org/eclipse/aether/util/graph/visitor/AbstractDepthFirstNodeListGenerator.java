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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * Abstract base class for depth first dependency tree traversers. Subclasses of this visitor will visit each node
 * exactly once regardless how many paths within the dependency graph lead to the node such that the resulting node
 * sequence is free of duplicates.
 * <p>
 * Actual vertex ordering (preorder, inorder, postorder) needs to be defined by subclasses through appropriate
 * implementations for {@link #visitEnter(org.eclipse.aether.graph.DependencyNode)} and
 * {@link #visitLeave(org.eclipse.aether.graph.DependencyNode)}
 */
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
        List<Dependency> dependencies = new ArrayList<>(getNodes().size());

        for (DependencyNode node : getNodes()) {
            Dependency dependency = node.getDependency();
            if (dependency != null) {
                if (includeUnresolved || dependency.getArtifact().getFile() != null) {
                    dependencies.add(dependency);
                }
            }
        }

        return dependencies;
    }

    /**
     * Gets the artifacts associated with the list of dependency nodes generated during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved artifacts shall be included in the result or not.
     * @return The list of artifacts, never {@code null}.
     */
    public List<Artifact> getArtifacts(boolean includeUnresolved) {
        List<Artifact> artifacts = new ArrayList<>(getNodes().size());

        for (DependencyNode node : getNodes()) {
            if (node.getDependency() != null) {
                Artifact artifact = node.getDependency().getArtifact();
                if (includeUnresolved || artifact.getFile() != null) {
                    artifacts.add(artifact);
                }
            }
        }

        return artifacts;
    }

    /**
     * Gets the files of resolved artifacts seen during the graph traversal.
     *
     * @return The list of artifact files, never {@code null}.
     */
    public List<File> getFiles() {
        List<File> files = new ArrayList<>(getNodes().size());

        for (DependencyNode node : getNodes()) {
            if (node.getDependency() != null) {
                File file = node.getDependency().getArtifact().getFile();
                if (file != null) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    /**
     * Gets a class path by concatenating the artifact files of the visited dependency nodes. Nodes with unresolved
     * artifacts are automatically skipped.
     *
     * @return The class path, using the platform-specific path separator, never {@code null}.
     */
    public String getClassPath() {
        StringBuilder buffer = new StringBuilder(1024);

        for (Iterator<DependencyNode> it = getNodes().iterator(); it.hasNext(); ) {
            DependencyNode node = it.next();
            if (node.getDependency() != null) {
                Artifact artifact = node.getDependency().getArtifact();
                if (artifact.getFile() != null) {
                    buffer.append(artifact.getFile().getAbsolutePath());
                    if (it.hasNext()) {
                        buffer.append(File.pathSeparatorChar);
                    }
                }
            }
        }

        return buffer.toString();
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
