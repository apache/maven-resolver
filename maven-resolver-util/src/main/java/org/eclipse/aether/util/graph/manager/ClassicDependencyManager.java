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

import java.util.Collection;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager that mimics the way Maven 2.x works. This manager was used throughout all Maven 3.x versions.
 * <p>
 * This manager has {@code deriveUntil=2} and {@code applyFrom=2}.
 * <p>
 * Note regarding transitivity: it is broken, and should not be used.
 */
public final class ClassicDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use constructor that provides consumer application specific predicate.
     */
    @Deprecated
    public ClassicDependencyManager() {
        this(null);
    }

    public ClassicDependencyManager(ScopeManager scopeManager) {
        this(false, scopeManager);
    }

    /**
     * Creates a new dependency manager without any management information.
     *
     * @param transitive If true, this manager will collect (derive) until last node on graph. If false,
     *                   it will work as original Maven 3 "classic" dependency manager, collect only up to
     *                   depth of 2.
     *
     * @since 2.0.0
     */
    public ClassicDependencyManager(boolean transitive, ScopeManager scopeManager) {
        super(transitive ? Integer.MAX_VALUE : 2, 2, scopeManager);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private ClassicDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Collection<Exclusion>> managedExclusions,
            SystemDependencyScope systemDependencyScope) {
        super(
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
    public DependencyManager deriveChildManager(DependencyCollectionContext context) {
        // MNG-4720: Maven2 backward compatibility
        // Removing this IF makes one IT fail here (read comment above):
        // https://github.com/apache/maven-integration-testing/blob/b4e8fd52b99a058336f9c7c5ec44fdbc1427759c/core-it-suite/src/test/java/org/apache/maven/it/MavenITmng4720DependencyManagementExclusionMergeTest.java#L67
        if (depth == 1) {
            return newInstance(managedVersions, managedScopes, managedOptionals, managedLocalPaths, managedExclusions);
        }
        return super.deriveChildManager(context);
    }

    @Override
    protected DependencyManager newInstance(
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Collection<Exclusion>> managedExclusions) {
        return new ClassicDependencyManager(
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
