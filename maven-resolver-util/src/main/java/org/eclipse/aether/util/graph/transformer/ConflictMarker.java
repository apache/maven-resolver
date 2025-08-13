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
package org.eclipse.aether.util.graph.transformer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that identifies conflicting dependencies. When this transformer has executed, the
 * transformation context holds a {@code Map<DependencyNode, String>} where dependency nodes that belong to the same
 * conflict group will have an equal conflict identifier. This map is stored using the key
 * {@link TransformationContextKeys#CONFLICT_IDS}.
 */
public final class ConflictMarker implements DependencyGraphTransformer {

    /**
     * After the execution of this method, every DependencyNode with an attached dependency is member of one conflict
     * group.
     *
     * @see DependencyGraphTransformer#transformGraph(DependencyNode, DependencyGraphTransformationContext)
     */
    @Override
    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) context.get(TransformationContextKeys.STATS);
        long time1 = System.nanoTime();

        Map<DependencyNode, Boolean> nodes = new IdentityHashMap<>(1024);
        Map<Key, ConflictGroup> groups = new HashMap<>(1024);

        analyze(node, nodes, groups, new int[] {0});

        long time2 = System.nanoTime();

        Map<DependencyNode, String> conflictIds = mark(nodes.keySet(), groups);

        context.put(TransformationContextKeys.CONFLICT_IDS, conflictIds);

        if (stats != null) {
            long time3 = System.nanoTime();
            stats.put("ConflictMarker.analyzeTime", time2 - time1);
            stats.put("ConflictMarker.markTime", time3 - time2);
            stats.put("ConflictMarker.nodeCount", nodes.size());
        }

        return node;
    }

    private void analyze(
            DependencyNode node, Map<DependencyNode, Boolean> nodes, Map<Key, ConflictGroup> groups, int[] counter) {
        if (nodes.put(node, Boolean.TRUE) != null) {
            return;
        }

        Set<Key> keys = getKeys(node);
        if (!keys.isEmpty()) {
            ConflictGroup group = null;
            boolean fixMappings = false;

            for (Key key : keys) {
                ConflictGroup g = groups.get(key);

                if (group != g) {
                    if (group == null) {
                        Set<Key> newKeys = merge(g.keys, keys);
                        if (newKeys == g.keys) {
                            group = g;
                            break;
                        } else {
                            group = new ConflictGroup(newKeys, counter[0]++);
                            fixMappings = true;
                        }
                    } else if (g == null) {
                        fixMappings = true;
                    } else {
                        Set<Key> newKeys = merge(g.keys, group.keys);
                        if (newKeys == g.keys) {
                            group = g;
                            fixMappings = false;
                            break;
                        } else if (newKeys != group.keys) {
                            group = new ConflictGroup(newKeys, counter[0]++);
                            fixMappings = true;
                        }
                    }
                }
            }

            if (group == null) {
                group = new ConflictGroup(keys, counter[0]++);
                fixMappings = true;
            }
            if (fixMappings) {
                for (Key key : group.keys) {
                    groups.put(key, group);
                }
            }
        }

        for (DependencyNode child : node.getChildren()) {
            analyze(child, nodes, groups, counter);
        }
    }

    private Set<Key> merge(Set<Key> keys1, Set<Key> keys2) {
        int size1 = keys1.size();
        int size2 = keys2.size();

        if (size1 < size2) {
            if (keys2.containsAll(keys1)) {
                return keys2;
            }
        } else {
            if (keys1.containsAll(keys2)) {
                return keys1;
            }
        }

        Set<Key> keys = new HashSet<>();
        keys.addAll(keys1);
        keys.addAll(keys2);
        return keys;
    }

    private Set<Key> getKeys(DependencyNode node) {
        Set<Key> keys;

        Dependency dependency = node.getDependency();

        if (dependency == null) {
            keys = Collections.emptySet();
        } else {
            Key key = toKey(dependency.getArtifact());

            if (node.getRelocations().isEmpty() && node.getAliases().isEmpty()) {
                keys = Collections.singleton(key);
            } else {
                keys = new HashSet<>();
                keys.add(key);

                for (Artifact relocation : node.getRelocations()) {
                    key = toKey(relocation);
                    keys.add(key);
                }

                for (Artifact alias : node.getAliases()) {
                    key = toKey(alias);
                    keys.add(key);
                }
            }
        }

        return keys;
    }

    private Map<DependencyNode, String> mark(Collection<DependencyNode> nodes, Map<Key, ConflictGroup> groups) {
        Map<DependencyNode, String> conflictIds = new IdentityHashMap<>(nodes.size() + 1);

        for (DependencyNode node : nodes) {
            Dependency dependency = node.getDependency();
            if (dependency != null) {
                Key key = toKey(dependency.getArtifact());
                conflictIds.put(
                        node, String.valueOf(groups.get(key).index).intern()); // interning it as is expected so in UT
            }
        }

        return conflictIds;
    }

    private static Key toKey(Artifact artifact) {
        return new Key(artifact);
    }

    static class ConflictGroup {

        final Set<Key> keys;

        final int index;

        ConflictGroup(Set<Key> keys, int index) {
            this.keys = keys;
            this.index = index;
        }

        @Override
        public String toString() {
            return String.valueOf(keys);
        }
    }

    static class Key {

        private final Artifact artifact;
        private final int hashCode;

        Key(Artifact artifact) {
            this.artifact = artifact;
            this.hashCode = Objects.hash(
                    artifact.getArtifactId(), artifact.getGroupId(), artifact.getExtension(), artifact.getClassifier());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (!(obj instanceof Key)) {
                return false;
            }
            Key that = (Key) obj;
            return artifact.getArtifactId().equals(that.artifact.getArtifactId())
                    && artifact.getGroupId().equals(that.artifact.getGroupId())
                    && artifact.getExtension().equals(that.artifact.getExtension())
                    && artifact.getClassifier().equals(that.artifact.getClassifier());
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return artifact.getGroupId()
                    + ':'
                    + artifact.getArtifactId()
                    + ':'
                    + artifact.getClassifier()
                    + ':'
                    + artifact.getExtension();
        }
    }
}
