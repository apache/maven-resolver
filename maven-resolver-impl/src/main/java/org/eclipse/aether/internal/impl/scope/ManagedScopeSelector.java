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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.impl.scope.InternalScopeManager;
import org.eclipse.aether.scope.DependencyScope;
import org.eclipse.aether.scope.ScopeManager;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictContext;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ConflictItem;
import org.eclipse.aether.util.graph.transformer.ConflictResolver.ScopeSelector;

import static java.util.Objects.requireNonNull;

/**
 * A scope selector for use with {@link ConflictResolver} that supports the scopes from {@link ScopeManager}.
 * In general, this selector picks the widest scope present among conflicting dependencies where e.g. "compile" is
 * wider than "runtime" which is wider than "test". If however a direct dependency is involved, its scope is selected.
 * <p>
 * This class also "bridges" between {@link DependencyScope} and Resolver that uses plain string labels for scopes.
 */
public final class ManagedScopeSelector extends ScopeSelector {
    private final DependencyScope systemScope;
    private final List<DependencyScope> dependencyScopesByWidthDescending;

    public ManagedScopeSelector(InternalScopeManager scopeManager) {
        requireNonNull(scopeManager, "scopeManager");
        this.systemScope = scopeManager.getSystemDependencyScope().orElse(null);
        this.dependencyScopesByWidthDescending =
                Collections.unmodifiableList(scopeManager.getDependencyScopeUniverse().stream()
                        .sorted(Comparator.comparing(scopeManager::getDependencyScopeWidth)
                                .reversed())
                        .collect(Collectors.toList()));
    }

    @Override
    public void selectScope(ConflictContext context) {
        String scope = context.getWinner().getDependency().getScope();
        if (systemScope == null || !systemScope.getId().equals(scope)) {
            scope = chooseEffectiveScope(context.getItems());
        }
        context.setScope(scope);
    }

    private String chooseEffectiveScope(Collection<ConflictItem> items) {
        Set<String> scopes = new HashSet<>();
        for (ConflictItem item : items) {
            if (item.getDepth() <= 1) {
                return item.getDependency().getScope();
            }
            scopes.addAll(item.getScopes());
        }
        return chooseEffectiveScope(scopes);
    }

    /**
     * Visible for testing. It chooses "widest" scope out of provided ones, unless system scope is present, in which
     * case system scope is selected.
     */
    public String chooseEffectiveScope(Set<String> scopes) {
        if (scopes.size() > 1 && systemScope != null) {
            scopes.remove(systemScope.getId());
        }

        String effectiveScope = "";

        if (scopes.size() == 1) {
            effectiveScope = scopes.iterator().next();
        } else {
            for (DependencyScope dependencyScope : dependencyScopesByWidthDescending) {
                if (scopes.contains(dependencyScope.getId())) {
                    effectiveScope = dependencyScope.getId();
                    break;
                }
            }
        }

        return effectiveScope;
    }
}
