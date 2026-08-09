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
 * Decides what dependencies to include in the dependency graph.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 * <p>
 * <em>Warning:</em> This hook is called from a hot spot and therefore implementations should pay attention to
 * performance. Among others, implementations should provide a semantic {@link Object#equals(Object) equals()} method.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getDependencySelector()
 * @see org.eclipse.aether.RepositorySystem#collectDependencies(org.eclipse.aether.RepositorySystemSession,
 *      CollectRequest)
 */
public interface DependencySelector {

    /**
     * Decides whether the specified dependency should be included in the dependency graph.
     *
     * @param dependency The dependency to check, must not be {@code null}.
     * @return {@code false} if the dependency should be excluded from the children of the current node, {@code true}
     *         otherwise.
     */
    boolean selectDependency(Dependency dependency);

    /**
     * Derives a dependency selector for the specified collection context. When calculating the child selector,
     * implementors are strongly advised to simply return the current instance if nothing changed to help save memory.
     *
     * @param context The dependency collection context, must not be {@code null}.
     * @return The dependency selector for the target node or {@code null} if dependencies should be unconditionally
     *         included in the sub graph.
     */
    DependencySelector deriveChildSelector(DependencyCollectionContext context);
}
