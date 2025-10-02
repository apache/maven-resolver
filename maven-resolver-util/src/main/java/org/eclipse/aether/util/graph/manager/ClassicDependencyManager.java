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

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagementKey;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager that mimics the way Maven 2.x works for backward compatibility.
 *
 * <h2>Overview</h2>
 * <p>
 * This manager was used throughout all Maven 3.x versions for backward compatibility reasons.
 * It provides the exact same dependency management behavior as Maven 2.x, which differs
 * significantly from modern dependency management approaches.
 * </p>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 * <li><strong>Exclusion Handling:</strong> Ignores exclusions introduced by direct dependencies</li>
 * <li><strong>Management Scope:</strong> Only obeys root management, ignoring intermediate management</li>
 * <li><strong>Depth Behavior:</strong> {@code deriveUntil=2}, {@code applyFrom=2} with special "hop" at {@code depth=1}</li>
 * <li><strong>Level 1 Skip:</strong> Ignores context from depth=1 for Maven 2.x compatibility</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <p>
 * Use this manager when you need exact Maven 2.x compatibility behavior or when working
 * with legacy projects that depend on Maven 2.x dependency resolution semantics.
 * </p>
 *
 * <h2>Comparison with Other Managers</h2>
 * <p>
 * Unlike {@link TransitiveDependencyManager} and {@link DefaultDependencyManager}, this manager
 * deliberately ignores certain dependency management rules to maintain backward compatibility.
 * See {@code MavenITmng4720DependencyManagementExclusionMergeTest} for behavioral differences.
 * </p>
 *
 * @see TransitiveDependencyManager
 * @see DefaultDependencyManager
 */
public final class ClassicDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use {@link #ClassicDependencyManager(ScopeManager)} instead to provide
     *             application-specific scope management. This constructor uses legacy system
     *             dependency scope handling which may not be appropriate for all use cases.
     */
    @Deprecated
    public ClassicDependencyManager() {
        this(null);
    }

    /**
     * Creates a new dependency manager without any management information.
     * <p>
     * This constructor initializes the manager with Maven 2.x compatible behavior:
     * <ul>
     * <li>deriveUntil = 2 (collect rules only from root level)</li>
     * <li>applyFrom = 2 (apply rules starting from depth 2)</li>
     * <li>Special depth=1 handling for backward compatibility</li>
     * </ul>
     *
     * @param scopeManager application-specific scope manager for handling system dependencies,
     *                     may be null to use legacy system dependency scope handling
     * @since 2.0.12
     */
    public ClassicDependencyManager(ScopeManager scopeManager) {
        super(2, 2, scopeManager);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private ClassicDependencyManager(
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

    /**
     * Derives a child manager with Maven 2.x compatibility behavior.
     * <p>
     * <strong>Critical Maven 2.x Compatibility:</strong> This method implements a special
     * "hop" at depth=1 that skips dependency management collection at that level. This
     * behavior is essential for Maven 2.x compatibility and is verified by integration tests.
     * </p>
     * <p>
     * <strong>Why the depth=1 skip is necessary:</strong> Maven 2.x did not collect dependency
     * management from first-level dependencies, only from the root. Removing this skip would
     * break backward compatibility with Maven 2.x projects.
     * </p>
     *
     * @param context the dependency collection context
     * @return a new child manager or the current instance with passed-through management
     * @see <a href="https://github.com/apache/maven-integration-testing/blob/master/core-it-suite/src/test/java/org/apache/maven/it/MavenITmng4720DependencyManagementExclusionMergeTest.java">MNG-4720 Integration Test</a>
     */
    @Override
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        // MNG-4720: Maven2 backward compatibility
        // Removing this IF makes one IT fail here (read comment above):
        // https://github.com/apache/maven-integration-testing/blob/b4e8fd52b99a058336f9c7c5ec44fdbc1427759c/core-it-suite/src/test/java/org/apache/maven/it/MavenITmng4720DependencyManagementExclusionMergeTest.java#L67
        // Skipping level=1 (maven2 compatibility); see MavenITmng4720DependencyManagementExclusionMergeTest
        if (depth == 1) {
            return newInstance(managedVersions, managedScopes, managedOptionals, managedLocalPaths, managedExclusions);
        }
        return super.deriveChildManager(context);
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
        return new ClassicDependencyManager(
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
