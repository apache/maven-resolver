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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that creates a topological sorting of the conflict ids which have been assigned to the
 * dependency nodes. Conflict ids are sorted according to the dependency relation induced by the dependency graph. This
 * transformer will query the key {@link TransformationContextKeys#CONFLICT_IDS} in the transformation context for an
 * existing mapping of nodes to their conflicts ids. In absence of this map, the transformer will automatically invoke
 * the {@link ConflictMarker} to calculate the conflict ids. When this transformer has executed, the transformation
 * context holds a {@code List<Object>} that denotes the topologically sorted conflict ids. The list will be stored
 * using the key {@link TransformationContextKeys#SORTED_CONFLICT_IDS}. In addition, the transformer will store a
 * {@code Collection<Collection<Object>>} using the key {@link TransformationContextKeys#CYCLIC_CONFLICT_IDS} that
 * describes cycles among conflict ids.
 */
public final class ConflictIdSorter implements DependencyGraphTransformer {

    @Override
    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        Map<?, ?> conflictIds = (Map<?, ?>) context.get(TransformationContextKeys.CONFLICT_IDS);
        if (conflictIds == null) {
            ConflictMarker marker = new ConflictMarker();
            marker.transformGraph(node, context);

            conflictIds = (Map<?, ?>) context.get(TransformationContextKeys.CONFLICT_IDS);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) context.get(TransformationContextKeys.STATS);
        long time1 = System.nanoTime();

        Map<Object, ConflictId> ids = new LinkedHashMap<>(256);

        ConflictId id = null;
        Object key = conflictIds.get(node);
        if (key != null) {
            id = new ConflictId(key, 0);
            ids.put(key, id);
        }

        Map<DependencyNode, Object> visited = new IdentityHashMap<>(conflictIds.size());

        buildConflictIdDAG(ids, node, id, 0, visited, conflictIds);

        long time2 = System.nanoTime();

        int cycles = topsortConflictIds(ids.values(), context);

        if (stats != null) {
            long time3 = System.nanoTime();
            stats.put("ConflictIdSorter.graphTime", time2 - time1);
            stats.put("ConflictIdSorter.topsortTime", time3 - time2);
            stats.put("ConflictIdSorter.conflictIdCount", ids.size());
            stats.put("ConflictIdSorter.conflictIdCycleCount", cycles);
        }

        return node;
    }

    private void buildConflictIdDAG(
            Map<Object, ConflictId> ids,
            DependencyNode node,
            ConflictId id,
            int depth,
            Map<DependencyNode, Object> visited,
            Map<?, ?> conflictIds) {
        if (visited.put(node, Boolean.TRUE) != null) {
            return;
        }

        depth++;

        for (DependencyNode child : node.getChildren()) {
            Object key = conflictIds.get(child);
            ConflictId childId = ids.get(key);
            if (childId == null) {
                childId = new ConflictId(key, depth);
                ids.put(key, childId);
            } else {
                childId.pullup(depth);
            }

            if (id != null) {
                id.add(childId);
            }

            buildConflictIdDAG(ids, child, childId, depth, visited, conflictIds);
        }
    }

    private int topsortConflictIds(Collection<ConflictId> conflictIds, DependencyGraphTransformationContext context) {
        List<Object> sorted = new ArrayList<>(conflictIds.size());

        RootQueue roots = new RootQueue(conflictIds.size() / 2);
        for (ConflictId id : conflictIds) {
            if (id.inDegree <= 0) {
                roots.add(id);
            }
        }

        processRoots(sorted, roots);

        boolean cycle = sorted.size() < conflictIds.size();

        while (sorted.size() < conflictIds.size()) {
            // cycle -> deal gracefully with nodes still having positive in-degree

            ConflictId nearest = null;
            for (ConflictId id : conflictIds) {
                if (id.inDegree <= 0) {
                    continue;
                }
                if (nearest == null
                        || id.minDepth < nearest.minDepth
                        || (id.minDepth == nearest.minDepth && id.inDegree < nearest.inDegree)) {
                    nearest = id;
                }
            }

            nearest.inDegree = 0;
            roots.add(nearest);

            processRoots(sorted, roots);
        }

        Collection<Collection<Object>> cycles = Collections.emptySet();
        if (cycle) {
            cycles = findCycles(conflictIds);
        }

        context.put(TransformationContextKeys.SORTED_CONFLICT_IDS, sorted);
        context.put(TransformationContextKeys.CYCLIC_CONFLICT_IDS, cycles);

        return cycles.size();
    }

    private void processRoots(List<Object> sorted, RootQueue roots) {
        while (!roots.isEmpty()) {
            ConflictId root = roots.remove();

            sorted.add(root.key);

            for (ConflictId child : root.children) {
                child.inDegree--;
                if (child.inDegree == 0) {
                    roots.add(child);
                }
            }
        }
    }

    private Collection<Collection<Object>> findCycles(Collection<ConflictId> conflictIds) {
        Collection<Collection<Object>> cycles = new HashSet<>();

        Map<Object, Integer> stack = new HashMap<>(128);
        Map<ConflictId, Object> visited = new IdentityHashMap<>(conflictIds.size());
        for (ConflictId id : conflictIds) {
            findCycles(id, visited, stack, cycles);
        }

        return cycles;
    }

    private void findCycles(
            ConflictId id,
            Map<ConflictId, Object> visited,
            Map<Object, Integer> stack,
            Collection<Collection<Object>> cycles) {
        Integer depth = stack.put(id.key, stack.size());
        if (depth != null) {
            stack.put(id.key, depth);
            Collection<Object> cycle = new HashSet<>();
            for (Map.Entry<Object, Integer> entry : stack.entrySet()) {
                if (entry.getValue() >= depth) {
                    cycle.add(entry.getKey());
                }
            }
            cycles.add(cycle);
        } else {
            if (visited.put(id, Boolean.TRUE) == null) {
                for (ConflictId childId : id.children) {
                    findCycles(childId, visited, stack, cycles);
                }
            }
            stack.remove(id.key);
        }
    }

    static final class ConflictId {

        final Object key;

        Collection<ConflictId> children = Collections.emptySet();

        int inDegree;

        int minDepth;

        ConflictId(Object key, int depth) {
            this.key = key;
            this.minDepth = depth;
        }

        public void add(ConflictId child) {
            if (children.isEmpty()) {
                children = new HashSet<>();
            }
            if (children.add(child)) {
                child.inDegree++;
            }
        }

        public void pullup(int depth) {
            if (depth < minDepth) {
                minDepth = depth;
                depth++;
                for (ConflictId child : children) {
                    child.pullup(depth);
                }
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof ConflictId)) {
                return false;
            }
            ConflictId that = (ConflictId) obj;
            return this.key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public String toString() {
            return key + " @ " + minDepth + " <" + inDegree;
        }
    }

    static final class RootQueue {

        private int nextOut;

        private int nextIn;

        private ConflictId[] ids;

        RootQueue(int capacity) {
            ids = new ConflictId[capacity + 16];
        }

        boolean isEmpty() {
            return nextOut >= nextIn;
        }

        void add(ConflictId id) {
            if (nextOut >= nextIn && nextOut > 0) {
                nextIn -= nextOut;
                nextOut = 0;
            }
            if (nextIn >= ids.length) {
                ConflictId[] tmp = new ConflictId[ids.length + ids.length / 2 + 16];
                System.arraycopy(ids, nextOut, tmp, 0, nextIn - nextOut);
                ids = tmp;
                nextIn -= nextOut;
                nextOut = 0;
            }
            int i;
            for (i = nextIn - 1; i >= nextOut && id.minDepth < ids[i].minDepth; i--) {
                ids[i + 1] = ids[i];
            }
            ids[i + 1] = id;
            nextIn++;
        }

        ConflictId remove() {
            return ids[nextOut++];
        }
    }
}
