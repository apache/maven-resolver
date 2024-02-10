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

import org.eclipse.aether.SystemScopeHandler;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Exclusion;

/**
 * A dependency manager managing transitive dependencies supporting transitive dependency management.
 * <p>
 * This manager is similar to "classic", it has {@code deriveUntil=Integer.MAX_VALUE} (unlike 2 as in "classic") and
 * {@code applyFrom=2}.
 *
 * @author Christian Schulte
 * @since 1.4.0
 */
public final class TransitiveDependencyManager extends AbstractDependencyManager {
    /**
     * Creates a new dependency manager without any management information.
     *
     * @deprecated Use constructor that provides consumer application specific predicate.
     */
    @Deprecated
    public TransitiveDependencyManager() {
        this(SYSTEM_PREDICATE);
    }

    public TransitiveDependencyManager(SystemScopeHandler systemScopeHandler) {
        super(Integer.MAX_VALUE, 2, systemScopeHandler);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private TransitiveDependencyManager(
            int depth,
            int deriveUntil,
            int applyFrom,
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions,
            SystemScopeHandler systemScopeHandler) {
        super(
                depth,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemScopeHandler);
    }

    @Override
    protected DependencyManager newInstance(
            Map<Object, String> managedVersions,
            Map<Object, String> managedScopes,
            Map<Object, Boolean> managedOptionals,
            Map<Object, String> managedLocalPaths,
            Map<Object, Collection<Exclusion>> managedExclusions) {
        return new TransitiveDependencyManager(
                depth + 1,
                deriveUntil,
                applyFrom,
                managedVersions,
                managedScopes,
                managedOptionals,
                managedLocalPaths,
                managedExclusions,
                systemScopeHandler);
    }
}
