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
package org.eclipse.aether.impl.scope;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.scope.DependencyScope;
import org.eclipse.aether.scope.ResolutionScope;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * Internal scope manager.
 *
 * @since 2.0.0
 */
public interface InternalScopeManager extends ScopeManager {
    /**
     * The "width" of scope: is basically sum of all distinct {@link ProjectPath} and {@link BuildPath} that are
     * in build scopes the scope is present in. The more of them, the "wider" is the scope. Transitive scopes are
     * weighted more as well.
     * <p>
     * The {@link ProjectPath#order()} makes given path "weigh" more. So a scope being present only in
     * "main" project path is wider than scope being present only in "test" project path.
     * <p>
     * Interpretation: the bigger the returned integer is, the "wider" the scope is. The numbers should not serve
     * any other purposes, merely to sort scope instances by "width" (i.e. from "widest" to "narrowest").
     */
    int getDependencyScopeWidth(DependencyScope dependencyScope);

    /**
     * Returns the {@link BuildScope} that this scope deem as main.
     */
    Optional<BuildScope> getDependencyScopeMainProjectBuildScope(DependencyScope dependencyScope);

    /**
     * Resolver specific: dependency selector to be used to support this scope (with its dependency
     * and resolution scopes).
     */
    DependencySelector getDependencySelector(RepositorySystemSession session, ResolutionScope resolutionScope);

    /**
     * Resolver specific: post-processing to be used to support this scope (with its dependency
     * and resolution scopes).
     */
    CollectResult postProcess(
            RepositorySystemSession session, ResolutionScope resolutionScope, CollectResult collectResult);

    /**
     * The mode of resolution scope: eliminate (remove all occurrences) or just remove.
     */
    enum Mode {
        /**
         * Mode where artifacts in non-wanted scopes are completely eliminated. In other words, this mode ensures
         * that if a dependency was removed due unwanted scope, it is guaranteed that no such dependency will appear
         * anywhere else in the resulting graph either.
         */
        ELIMINATE,

        /**
         * Mode where artifacts in non-wanted scopes are removed only. In other words, they will NOT prevent (as in
         * they will not "dominate") other possibly appearing occurrences of same artifact in the graph.
         */
        REMOVE
    }

    /**
     * Creates dependency scope instance.
     * <p>
     * Should be invoked only via {@link ScopeManagerConfiguration#buildDependencyScopes(InternalScopeManager)}.
     */
    DependencyScope createDependencyScope(String id, boolean transitive, Collection<BuildScopeQuery> presence);

    /**
     * Creates system dependency scope instance. This method may be invoked only once, as there can be only one
     * instance of {@link SystemDependencyScope}!
     * <p>
     * Should be invoked only via {@link ScopeManagerConfiguration#buildDependencyScopes(InternalScopeManager)}.
     */
    SystemDependencyScope createSystemDependencyScope(
            String id, boolean transitive, Collection<BuildScopeQuery> presence, String systemPathProperty);

    /**
     * Creates resolution scope instance.
     * <p>
     * Should be invoked only via {@link ScopeManagerConfiguration#buildResolutionScopes(InternalScopeManager)}.
     */
    ResolutionScope createResolutionScope(
            String id,
            Mode mode,
            Collection<BuildScopeQuery> wantedPresence,
            Collection<DependencyScope> explicitlyIncluded,
            Collection<DependencyScope> transitivelyExcluded);
}
