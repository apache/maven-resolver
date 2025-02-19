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

/**
 * A collection of keys used by the dependency graph transformers when exchanging information via the graph
 * transformation context.
 *
 * @see org.eclipse.aether.collection.DependencyGraphTransformationContext#get(Object)
 */
public final class TransformationContextKeys {

    /**
     * The key in the graph transformation context where a {@code Map<DependencyNode, Object>} is stored which maps
     * dependency nodes to their conflict ids. All nodes that map to an equal conflict id belong to the same group of
     * conflicting dependencies. Note that the map keys use reference equality.
     *
     * @see ConflictMarker
     */
    public static final Object CONFLICT_IDS = "conflictIds";

    /**
     * The key in the graph transformation context where a {@code List<Object>} is stored that denotes a topological
     * sorting of the conflict ids.
     *
     * @see ConflictIdSorter
     */
    public static final Object SORTED_CONFLICT_IDS = "sortedConflictIds";

    /**
     * The key in the graph transformation context where a {@code Collection<Collection<Object>>} is stored that denotes
     * cycles among conflict ids. Each element in the outer collection denotes one cycle, i.e. if the collection is
     * empty, the conflict ids have no cyclic dependencies.
     *
     * @see ConflictIdSorter
     */
    public static final Object CYCLIC_CONFLICT_IDS = "cyclicConflictIds";

    /**
     * The key in the graph transformation context where a {@code Map<String, Object>} is stored that can be used to
     * include some runtime/performance stats in the debug log. If this map is not present, no stats should be recorded.
     */
    public static final Object STATS = "stats";

    private TransformationContextKeys() {
        // hide constructor
    }
}
