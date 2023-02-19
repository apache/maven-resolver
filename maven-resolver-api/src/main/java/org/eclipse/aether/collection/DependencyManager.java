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
 * Applies dependency management to the dependencies of a dependency node.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 * <p>
 * <em>Warning:</em> This hook is called from a hot spot and therefore implementations should pay attention to
 * performance. Among others, implementations should provide a semantic {@link Object#equals(Object) equals()} method.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getDependencyManager()
 * @see org.eclipse.aether.RepositorySystem#collectDependencies(org.eclipse.aether.RepositorySystemSession,
 *      CollectRequest)
 */
public interface DependencyManager {

    /**
     * Applies dependency management to the specified dependency.
     *
     * @param dependency The dependency to manage, must not be {@code null}.
     * @return The management update to apply to the dependency or {@code null} if the dependency is not managed at all.
     */
    DependencyManagement manageDependency(Dependency dependency);

    /**
     * Derives a dependency manager for the specified collection context. When calculating the child manager,
     * implementors are strongly advised to simply return the current instance if nothing changed to help save memory.
     *
     * @param context The dependency collection context, must not be {@code null}.
     * @return The dependency manager for the dependencies of the target node or {@code null} if dependency management
     *         should no longer be applied.
     */
    DependencyManager deriveChildManager(DependencyCollectionContext context);
}
