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
package org.eclipse.aether.util.graph.manager;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.collection.DependencyManagementKey;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager that applies management at all levels with aggressive transitive behavior.
 *
 * <h2>Overview</h2>
 * <p>
 * This manager provides the most aggressive dependency management approach, applying management
 * rules at every level of the dependency graph. Unlike other managers, it starts applying
 * management from the very first level (depth=0) and continues indefinitely.
 * </p>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 * <li><strong>Aggressive Application:</strong> {@code deriveUntil=Integer.MAX_VALUE}, {@code applyFrom=0}</li>
 * <li><strong>First Level Management:</strong> Applies management even at the root level</li>
 * <li><strong>ModelBuilder Interference:</strong> Ignores and overrides ModelBuilder's work</li>
 * <li><strong>Complete Transitivity:</strong> Manages dependencies at all depths</li>
 * </ul>
 *
 * <h2>When NOT to Use</h2>
 * <p>
 * <strong>⚠️ Warning:</strong> This manager is <em>not recommended for Maven or Maven-like use cases</em>
 * because it interferes with ModelBuilder, potentially rewriting models that ModelBuilder has
 * already processed correctly. This can lead to unexpected dependency resolution behavior.
 * </p>
 *
 * <h2>When to Use</h2>
 * <p>
 * Consider this manager only for non-Maven scenarios where you need complete control over
 * dependency management at all levels and are not using Maven's ModelBuilder.
 * </p>
 *
 * <h2>Comparison with Other Managers</h2>
 * <ul>
 * <li>{@link ClassicDependencyManager}: Maven 2.x compatibility, limited scope</li>
 * <li>{@link TransitiveDependencyManager}: Proper transitive management, ModelBuilder-friendly</li>
 * <li><strong>This manager:</strong> Aggressive, all-level management (use with caution)</li>
 * </ul>
 *
 * @author Christian Schulte
 * @since 1.4.0
 * @see ClassicDependencyManager
 * @see TransitiveDependencyManager
 */
public final class DefaultDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use {@link #DefaultDependencyManager(ScopeManager)} instead to provide
     *             application-specific scope management. This constructor uses legacy system
     *             dependency scope handling.
     */
    @Deprecated
    public DefaultDependencyManager() {
        this(null);
    }

    /**
     * Creates a new dependency manager with aggressive management behavior.
     * <p>
     * <strong>⚠️ Warning:</strong> This manager is not recommended for Maven use cases.
     * It initializes with the most aggressive settings:
     * <ul>
     * <li>deriveUntil = Integer.MAX_VALUE (always collect management rules)</li>
     * <li>applyFrom = 0 (apply management from the very first level)</li>
     * <li>No respect for ModelBuilder's work</li>
     * </ul>
     *
     * @param scopeManager application-specific scope manager for handling system dependencies,
     *                     may be null to use legacy system dependency scope handling
     */
    public DefaultDependencyManager(ScopeManager scopeManager) {
        super(Integer.MAX_VALUE, 0, scopeManager);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private DefaultDependencyManager(
            ArrayList<AbstractDependencyManager> path,
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<DependencyManagementKey, String> managedVersions,
            MMap<DependencyManagementKey, String> managedScopes,
            MMap<DependencyManagementKey, Boolean> managedOptionals,
            MMap<DependencyManagementKey, String> managedLocalPaths,
            MMap<DependencyManagementKey, Holder<Collection<Exclusion>>> managedExclusions,
            SystemDependencyScope systemDependencyScope) {
        super(
                path,
                depth,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemDependencyScope);
    }

    @Override
    protected DependencyManager newInstance(
            MMap<DependencyManagementKey, String> managedVersions,
            MMap<DependencyManagementKey, String> managedScopes,
            MMap<DependencyManagementKey, Boolean> managedOptionals,
            MMap<DependencyManagementKey, String> managedLocalPaths,
            MMap<DependencyManagementKey, Holder<Collection<Exclusion>>> managedExclusions) {
        ArrayList<AbstractDependencyManager> path = new ArrayList<>(this.path);
        path.add(this);
        return new DefaultDependencyManager(
                path,
                depth + 1,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemDependencyScope);
    }
}
