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
import java.util.Map;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.scope.SystemDependencyScope;

/**
 * A dependency manager managing dependencies on all levels supporting transitive dependency management.
 * <p>
 * <b>Note:</b>Unlike the {@code ClassicDependencyManager} and the {@code TransitiveDependencyManager} this
 * implementation applies management also on the first level. This is considered the resolver's default behaviour.
 * It ignores all management overrides supported by the {@code MavenModelBuilder}.
 * <p>
 * This manager has {@code deriveUntil=Integer.MAX_VALUE} and {@code applyFrom=0}.
 *
 * @author Christian Schulte
 * @since 1.4.0
 */
public final class DefaultDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use constructor that provides consumer application specific predicate.
     */
    @Deprecated
    public DefaultDependencyManager() {
        this(null);
    }

    public DefaultDependencyManager(ScopeManager scopeManager) {
        super(Integer.MAX_VALUE, 0, scopeManager);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private DefaultDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions,
            SystemDependencyScope systemDependencyScope,
            DependencyCollectionContext currentContext) {
        super(
                depth,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemDependencyScope,
                currentContext);
    }

    @Override
    protected DependencyManager newInstance(
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions,
            DependencyCollectionContext currentContext) {
        return new DefaultDependencyManager(
                depth + 1,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemDependencyScope,
                currentContext);
    }
}
