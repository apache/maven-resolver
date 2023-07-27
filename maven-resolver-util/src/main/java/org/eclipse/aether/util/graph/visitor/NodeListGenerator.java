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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.stream.Collectors.toList;

/**
 * Node list generator usable with different traversing strategies.
 *
 * @since TBD
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
        return nodes.stream().filter(d -> d.getDependency() != null).collect(toList());
    }

    /**
     * Gets the dependencies seen during the graph traversal.
     *
     * @param includeUnresolved Whether unresolved dependencies shall be included in the result or not.
     * @return The list of dependencies, never {@code null}.
     */
    public List<Dependency> getDependencies(boolean includeUnresolved) {
        List<Dependency> dependencies = new ArrayList<>(getNodes().size());
        for (DependencyNode node : getNodesWithDependencies()) {
            Dependency dependency = node.getDependency();
            if (includeUnresolved || dependency.getArtifact().getFile() != null) {
                dependencies.add(dependency);
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
        for (DependencyNode node : getNodesWithDependencies()) {
            Artifact artifact = node.getDependency().getArtifact();
            if (includeUnresolved || artifact.getFile() != null) {
                artifacts.add(artifact);
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
        for (DependencyNode node : getNodesWithDependencies()) {
            File file = node.getDependency().getArtifact().getFile();
            if (file != null) {
                files.add(file);
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
        for (Iterator<DependencyNode> it = getNodesWithDependencies().iterator(); it.hasNext(); ) {
            DependencyNode node = it.next();
            Artifact artifact = node.getDependency().getArtifact();
            if (artifact.getFile() != null) {
                buffer.append(artifact.getFile().getAbsolutePath());
                if (it.hasNext()) {
                    buffer.append(File.pathSeparatorChar);
                }
            }
        }
        return buffer.toString();
    }
}
