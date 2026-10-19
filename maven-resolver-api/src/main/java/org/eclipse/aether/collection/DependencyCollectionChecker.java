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

import org.eclipse.aether.RepositorySystemSession;

/**
 * Dependency collector checker. It is able to check dependency collection result, deem it "satisfiable" or
 * augment collection and re-execute it.
 *
 * @since 2.0.19
 */
public interface DependencyCollectionChecker {
    /**
     * A default "no op" implementation.
     */
    DependencyCollectionChecker NOOP = new DependencyCollectionChecker() {};

    /**
     * Config property for collector checker suppression. Presence of this key will suppress collection checking.
     * This key is not meant for users, but to programmatically signal collection suppression.
     */
    String COLLECTOR_CHECKER_SUPPRESSED = "aether.dependencyCollector.checker.suppressed";

    /**
     * Prepares for dependency collection.
     *
     * @param session The repository system session, must not be {@code null}.
     * @param request The collect request about to be processed, must not be {@code null}.
     * @return The session to use for the collection, possibly adapted by this checker.
     */
    default RepositorySystemSession prepare(RepositorySystemSession session, CollectRequest request) {
        return session;
    }

    /**
     * Performs checks on finished dependency collection. It should return {@code true} if the collection was deemed
     * "satisfactory". If should return {@code false} <em>only, if collection was not satisfactory, and checker
     * was able to modify resolution parameters (to not repeat same work)</em>. In other cases (not satisfactory
     * but no param change would help) it should throw {@link DependencyCollectionException}.
     *
     * @param session The repository system session, must not be {@code null}.
     * @param request The collect request that was processed, must not be {@code null}.
     * @param result The result of the dependency collection, must not be {@code null}.
     * @return {@code true} if the collection result is satisfactory, {@code false} if the checker adjusted
     *         resolution parameters and collection should be repeated.
     * @throws DependencyCollectionException If the collection is not satisfactory and cannot be remedied.
     */
    default boolean isSatisfactory(RepositorySystemSession session, CollectRequest request, CollectResult result)
            throws DependencyCollectionException {
        return true;
    }
}
