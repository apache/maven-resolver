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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.stream.Collectors.toList;

/**
 * Different transformations for {@link List<DependencyNode>}.
 *
 * @since TBD
 */
final class DependencyNodesUtilities {

    /**
     * Gets the list of dependency nodes that was generated during the graph traversal and have {@code non-null}
     * {@link DependencyNode#getDependency()}.
     *
     * @param nodes The collection of dependency nodes, never {@code null}.
     * @return The list of dependency nodes having dependency, never {@code null}.
     */
    static List<DependencyNode> getNodesWithDependencies(List<DependencyNode> nodes) {
        return nodes.stream().filter(d -> d.getDependency() != null).collect(toList());
    }

    /**
     * Gets the dependencies seen during the graph traversal.
     *
     * @param nodes The collection of dependency nodes, never {@code null}.
     * @param includeUnresolved Whether unresolved dependencies shall be included in the result or not.
     * @return The list of dependencies, never {@code null}.
     */
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

    /**
     * Gets the artifacts associated with the list of dependency nodes generated during the graph traversal.
     *
     * @param nodes The collection of dependency nodes, never {@code null}.
     * @param includeUnresolved Whether unresolved artifacts shall be included in the result or not.
     * @return The list of artifacts, never {@code null}.
     */
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

    /**
     * Gets the files of resolved artifacts seen during the graph traversal.
     *
     * @param nodes The collection of dependency nodes, never {@code null}.
     * @return The list of artifact files, never {@code null}.
     */
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

    /**
     * Gets a class path by concatenating the artifact files of the visited dependency nodes. Nodes with unresolved
     * artifacts are automatically skipped.
     *
     * @param nodes The collection of dependency nodes, never {@code null}.
     * @return The class path, using the platform-specific path separator, never {@code null}.
     */
    static String getClassPath(List<DependencyNode> nodes) {
        StringBuilder buffer = new StringBuilder(1024);
        for (Iterator<DependencyNode> it = getNodesWithDependencies(nodes).iterator(); it.hasNext(); ) {
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
