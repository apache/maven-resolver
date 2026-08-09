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
package org.eclipse.aether.graph;

import java.util.List;

/**
 * A cycle within a dependency graph, that is a sequence of dependencies d_1, d_2, ..., d_n where d_1 and d_n have the
 * same versionless coordinates. In more practical terms, a cycle occurs when a project directly or indirectly depends
 * on its own output artifact.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyCycle {

    /**
     * Gets the dependencies that lead to the first dependency on the cycle, starting from the root of the dependency
     * graph.
     *
     * @return The (read-only) sequence of dependencies that precedes the cycle in the graph, potentially empty but
     *         never {@code null}.
     */
    List<Dependency> getPrecedingDependencies();

    /**
     * Gets the dependencies that actually form the cycle. For example, a -&gt; b -&gt; c -&gt; a, i.e. the last
     * dependency in this sequence duplicates the first element and closes the cycle. Hence the length of the cycle is
     * the size of the returned sequence minus 1.
     *
     * @return The (read-only) sequence of dependencies that forms the cycle, never {@code null}.
     */
    List<Dependency> getCyclicDependencies();
}
