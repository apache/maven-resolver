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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;

/**
 * Given a collection of direct dependencies, recursively gathers their transitive dependencies and calculates the
 * dependency graph.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface DependencyCollector {

    /**
     * Collects the transitive dependencies of some artifacts and builds a dependency graph. Note that this operation is
     * only concerned about determining the coordinates of the transitive dependencies and does not actually resolve the
     * artifact files. The supplied session carries various hooks to customize the dependency graph that must be invoked
     * throughout the operation.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The collection request, must not be {@code null}.
     * @return The collection result, never {@code null}.
     * @throws DependencyCollectionException If the dependency tree could not be built.
     * @see RepositorySystemSession#getDependencyTraverser()
     * @see RepositorySystemSession#getDependencyManager()
     * @see RepositorySystemSession#getDependencySelector()
     * @see RepositorySystemSession#getVersionFilter()
     * @see RepositorySystemSession#getDependencyGraphTransformer()
     * @see RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)
     */
    CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
            throws DependencyCollectionException;
}
