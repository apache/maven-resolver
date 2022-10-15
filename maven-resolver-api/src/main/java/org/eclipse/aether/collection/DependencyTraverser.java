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
package org.eclipse.aether.collection;

import org.eclipse.aether.graph.Dependency;

/**
 * Decides whether the dependencies of a dependency node should be traversed as well.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 * <p>
 * <em>Warning:</em> This hook is called from a hot spot and therefore implementations should pay attention to
 * performance. Among others, implementations should provide a semantic {@link Object#equals(Object) equals()} method.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getDependencyTraverser()
 * @see org.eclipse.aether.RepositorySystem#collectDependencies(org.eclipse.aether.RepositorySystemSession,
 *      CollectRequest)
 */
public interface DependencyTraverser {

    /**
     * Decides whether the dependencies of the specified dependency should be traversed.
     *
     * @param dependency The dependency to check, must not be {@code null}.
     * @return {@code true} if the dependency graph builder should recurse into the specified dependency and process its
     *         dependencies, {@code false} otherwise.
     */
    boolean traverseDependency(Dependency dependency);

    /**
     * Derives a dependency traverser that will be used to decide whether the transitive dependencies of the dependency
     * given in the collection context shall be traversed. When calculating the child traverser, implementors are
     * strongly advised to simply return the current instance if nothing changed to help save memory.
     *
     * @param context The dependency collection context, must not be {@code null}.
     * @return The dependency traverser for the target node or {@code null} if dependencies should be unconditionally
     *         traversed in the sub graph.
     */
    DependencyTraverser deriveChildTraverser(DependencyCollectionContext context);
}
