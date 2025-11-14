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

import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager that provides proper transitive dependency management for modern Maven usage.
 *
 * <h2>Overview</h2>
 * <p>
 * This manager implements proper "transitive dependency management" that works harmoniously
 * with Maven's ModelBuilder. It produces more precise results regarding versions by respecting
 * transitive management rules while allowing higher-level management to override lower-level rules.
 * </p>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 * <li><strong>Transitive Management:</strong> {@code deriveUntil=Integer.MAX_VALUE}, {@code applyFrom=2}</li>
 * <li><strong>ModelBuilder Friendly:</strong> Works in conjunction with, not against, ModelBuilder</li>
 * <li><strong>Inheritance Aware:</strong> Special handling for scope and optional properties</li>
 * <li><strong>Precise Versioning:</strong> Obeys transitive management unless managed at higher levels</li>
 * </ul>
 *
 * <h2>Inheritance Handling</h2>
 * <p>
 * This manager provides special care for "scope" and "optional" properties that are subject
 * to inheritance in the dependency graph during later graph transformation steps. These
 * properties are only derived from the root to prevent interference with inheritance logic.
 * </p>
 *
 * <h2>When to Use</h2>
 * <p>
 * This is the <strong>recommended manager for modern Maven projects</strong> that need proper
 * transitive dependency management while maintaining compatibility with Maven's ModelBuilder.
 * </p>
 *
 * <h2>Comparison with Other Managers</h2>
 * <ul>
 * <li>{@link ClassicDependencyManager}: Maven 2.x compatibility, limited transitive support</li>
 * <li>{@link DefaultDependencyManager}: Aggressive but interferes with ModelBuilder</li>
 * <li><strong>This manager:</strong> Modern, transitive, ModelBuilder-compatible (recommended)</li>
 * </ul>
 *
 * @author Christian Schulte
 * @since 1.4.0
 * @see ClassicDependencyManager
 * @see DefaultDependencyManager
 */
public final class TransitiveDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use {@link #TransitiveDependencyManager(ScopeManager)} instead to provide
     *             application-specific scope management. This constructor uses legacy system
     *             dependency scope handling.
     */
    @Deprecated
    public TransitiveDependencyManager() {
        this(null);
    }

    /**
     * Creates a new transitive dependency manager with ModelBuilder-compatible behavior.
     * <p>
     * This constructor initializes the manager with settings optimized for modern Maven usage:
     * <ul>
     * <li>deriveUntil = Integer.MAX_VALUE (collect management rules at all levels)</li>
     * <li>applyFrom = 2 (apply management starting from depth 2, respecting ModelBuilder)</li>
     * <li>Special inheritance handling for scope and optional properties</li>
     * </ul>
     *
     * @param scopeManager application-specific scope manager for handling system dependencies,
     *                     may be null to use legacy system dependency scope handling
     */
    public TransitiveDependencyManager(ScopeManager scopeManager) {
        super(Integer.MAX_VALUE, 2, scopeManager);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private TransitiveDependencyManager(
            ArrayList<AbstractDependencyManager> path,
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions,
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
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions) {
        ArrayList<AbstractDependencyManager> path = new ArrayList<>(this.path);
        path.add(this);
        return new TransitiveDependencyManager(
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
