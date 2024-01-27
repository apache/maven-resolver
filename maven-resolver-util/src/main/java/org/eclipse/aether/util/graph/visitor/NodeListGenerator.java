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
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.stream.Collectors.toList;

/**
 * Node list generator usable with different traversing strategies. It is wrapped {@link List}{@code <DependencyNode>} but
 * offers several transformations, that are handy.
 *
 * @since 2.0.0
 *
 * @see PreorderDependencyNodeConsumerVisitor
 * @see PostorderDependencyNodeConsumerVisitor
 * @see LevelOrderDependencyNodeConsumerVisitor
 */
public final class NodeListGenerator implements Consumer<DependencyNode> {

    private final ArrayList<DependencyNode> nodes;

    public NodeListGenerator() {
        nodes = new ArrayList<>(128);
    }

    @Override
    public void accept(DependencyNode dependencyNode) {
        nodes.add(dependencyNode);
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
     * Gets the list of dependency nodes that was generated during the graph traversal and have {@code non-null}
     * {@link DependencyNode#getDependency()}.
     *
     * @return The list of dependency nodes having dependency, never {@code null}.
     */
    public List<DependencyNode> getNodesWithDependencies() {
        return getNodesWithDependencies(getNodes());
    }

    /**
     * Gets the dependencies seen during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved dependencies shall be included in the result or not.
     * @return The list of dependencies, never {@code null}.
     */
    public List<Dependency> getDependencies(boolean includeUnresolved) {
        return getDependencies(getNodes(), includeUnresolved);
    }

    /**
     * Gets the artifacts associated with the list of dependency nodes generated during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved artifacts shall be included in the result or not.
     * @return The list of artifacts, never {@code null}.
     */
    public List<Artifact> getArtifacts(boolean includeUnresolved) {
        return getArtifacts(getNodes(), includeUnresolved);
    }

    /**
     * Gets the files of resolved artifacts seen during the graph traversal.
     *
     * @return The list of artifact files, never {@code null}.
     */
    public List<File> getFiles() {
        return getFiles(getNodes());
    }

    /**
     * Gets a class path by concatenating the artifact files of the visited dependency nodes. Nodes with unresolved
     * artifacts are automatically skipped.
     *
     * @return The class path, using the platform-specific path separator, never {@code null}.
     */
    public String getClassPath() {
        return getClassPath(getNodes());
    }

    static List<DependencyNode> getNodesWithDependencies(List<DependencyNode> nodes) {
        return nodes.stream().filter(d -> d.getDependency() != null).collect(toList());
    }

    static List<Dependency> getDependencies(List<DependencyNode> nodes, boolean includeUnresolved) {
        List<Dependency> dependencies = new ArrayList<>(nodes.size());
        for (DependencyNode node : getNodesWithDependencies(nodes)) {
            Dependency dependency = node.getDependency();
            if (includeUnresolved || dependency.getArtifact().getFile() != null) {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    static List<Artifact> getArtifacts(List<DependencyNode> nodes, boolean includeUnresolved) {
        List<Artifact> artifacts = new ArrayList<>(nodes.size());
        for (DependencyNode node : getNodesWithDependencies(nodes)) {
            Artifact artifact = node.getDependency().getArtifact();
            if (includeUnresolved || artifact.getFile() != null) {
                artifacts.add(artifact);
            }
        }

        return artifacts;
    }

    static List<File> getFiles(List<DependencyNode> nodes) {
        List<File> files = new ArrayList<>(nodes.size());
        for (DependencyNode node : getNodesWithDependencies(nodes)) {
            File file = node.getDependency().getArtifact().getFile();
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    static String getClassPath(List<DependencyNode> nodes) {
        StringBuilder buffer = new StringBuilder(1024);
        for (DependencyNode node : getNodesWithDependencies(nodes)) {
            Artifact artifact = node.getDependency().getArtifact();
            if (artifact.getFile() != null) {
                if (buffer.length() != 0) {
                    buffer.append(File.pathSeparatorChar);
                }
                buffer.append(artifact.getFile().getAbsolutePath());
            }
        }
        return buffer.toString();
    }
}
