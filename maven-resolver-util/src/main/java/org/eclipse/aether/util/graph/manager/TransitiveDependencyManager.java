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
 * A dependency manager managing transitive dependencies supporting transitive dependency management.
 * <p>
 * This manager applies proper "transitive dependency management", and produces more precise results regarding
 * versions (as it obeys transitive management unless managed higher).
 * <p>
 * This manager derives always and applies from {@code depth=2} with special care for two "scope" and "optional" that are
 * having applied inheritance in dependency graph in later step, during graph transformation.
 *
 * @author Christian Schulte
 * @since 1.4.0
 */
public final class TransitiveDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated use constructor that provides consumer application specific predicate
     */
    @Deprecated
    public TransitiveDependencyManager() {
        this(null);
    }

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

    /**
     * The "scope" and "optional" are special: because in dependency graph these two properties are subject to inheritance
     * (which is out of scope for model builder), the "scope" and "optional" is derived only from the root.
     * <p>
     * Hence, collection of "scope" and "optional" stops on root (the "scope" has special case for "system" scope)
     * as if we would manage these from management sources below root (that would mean we would mark nodes "managed"
     * in dependency graph), we would prevent proper application of inheritance later on graph transformation, as
     * "managed" flag means "do not touch it, it is as it should be", making those nodes end up in wrong "scopes" or
     * "optional" states.
     */
    @Override
    protected boolean isInheritedDerived() {
        return depth == 0;
    }
}
