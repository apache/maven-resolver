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
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager that mimics the way Maven 2.x works. This manager was used throughout all Maven 3.x versions as
 * well for backward compatibility reasons. The biggest difference between this manager and others is that this
 * ignores exclusions introduced by direct dependencies. To see differences in action, check out
 * {@code MavenITmng4720DependencyManagementExclusionMergeTest} IT class.
 * <p>
 * This manager has {@code deriveUntil=2} and {@code applyFrom=2} with "hop" on {@code depth=1} (ignores context from that level).
 * This manager obeys only root management and nothing else, and applies them from {@code depth=2} and below.
 */
public final class ClassicDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated use constructor that provides consumer application specific scope manager
     */
    @Deprecated
    public ClassicDependencyManager() {
        this(null);
    }

    /**
     * Creates a new dependency manager without any management information.
     *
     * @param scopeManager application specific scope manager
     *
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
            MMap<Key, String> managedVersions,
            MMap<Key, String> managedScopes,
            MMap<Key, Boolean> managedOptionals,
            MMap<Key, String> managedLocalPaths,
            MMap<Key, Holder<Collection<Exclusion>>> managedExclusions) {
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
