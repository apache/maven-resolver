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
package org.eclipse.aether.internal.impl.scope;

import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.scope.DependencyScope;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeDeriver;

import static java.util.Objects.requireNonNull;

/**
 * A scope deriver for use with {@link ConflictResolver} that supports the scopes from {@link ScopeManager}. It basically
 * chooses "narrowest" scope, based on parent and child scopes.
 * <p>
 * This class also "bridges" between {@link DependencyScope} and Resolver that uses plain string labels for scopes.
 *
 * @since 4.0.0
 */
public final class ManagedScopeDeriver extends ScopeDeriver {
    private final InternalScopeManager scopeManager;
    private final DependencyScope systemScope;

    public ManagedScopeDeriver(InternalScopeManager scopeManager) {
        this.scopeManager = requireNonNull(scopeManager, "scopeManager");
        this.systemScope = scopeManager.getSystemScope().orElse(null);
    }

    @Override
    public void deriveScope(ScopeContext context) {
        context.setDerivedScope(getDerivedScope(context.getParentScope(), context.getChildScope()));
    }

    /**
     * Visible for testing. It chooses "narrowest" scope out of parent or child, unless child is system scope.
     */
    public String getDerivedScope(String parentScope, String childScope) {
        // ask parent scope (nullable)
        DependencyScope parent = parentScope != null
                ? scopeManager.getDependencyScope(parentScope).orElse(null)
                : null;
        // ask child scope (non-nullable, but may be unknown scope to manager)
        DependencyScope child = scopeManager.getDependencyScope(childScope).orElse(null);

        // if system scope exists and child is system scope: system
        if (systemScope != null && systemScope == child) {
            return systemScope.getId();
        }
        // if no parent (i.e. is root): child scope as-is
        if (parent == null) {
            return child != null ? child.getId() : "";
        }
        if (child == null) {
            return parent.getId();
        }
        // otherwise the narrowest out of parent or child
        int parentWidth = scopeManager.getDependencyScopeWidth(parent);
        int childWidth = scopeManager.getDependencyScopeWidth(child);
        if (parentWidth < childWidth) {
            return parent.getId();
        } else {
            return child.getId();
        }
    }
}
