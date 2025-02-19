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

import java.util.Comparator;
import java.util.stream.Collectors;

import org.eclipse.aether.impl.scope.BuildScope;
import org.eclipse.aether.impl.scope.ScopeManagerConfiguration;
import org.eclipse.aether.scope.ResolutionScope;

import static org.eclipse.aether.impl.scope.BuildScopeQuery.all;

/**
 * This is a diagnostic tool to dump out scope manager states.
 */
public final class ScopeManagerDump {

    /**
     * Invoke this method with configuration and this class will dump its interpretation.
     */
    public static void dump(ScopeManagerConfiguration configuration) {
        new ScopeManagerDump(configuration);
    }

    private ScopeManagerDump(ScopeManagerConfiguration configuration) {
        ScopeManagerImpl scopeManager = new ScopeManagerImpl(configuration);
        System.out.println();
        dumpBuildScopes(scopeManager);
        System.out.println();
        dumpDependencyScopes(scopeManager);
        System.out.println();
        dumpDependencyScopeDerives(scopeManager);
        System.out.println();
        dumpResolutionScopes(scopeManager);
    }

    private void dumpBuildScopes(ScopeManagerImpl scopeManager) {
        System.out.println(scopeManager.getId() + " defined build scopes:");
        scopeManager.getBuildScopeSource().query(all()).stream()
                .sorted(Comparator.comparing(BuildScope::order))
                .forEach(s -> System.out.println(s.getId() + " (order=" + s.order() + ")"));
    }

    private void dumpDependencyScopes(ScopeManagerImpl scopeManager) {
        System.out.println(scopeManager.getId() + " defined dependency scopes:");
        scopeManager.getDependencyScopeUniverse().stream()
                .sorted(Comparator.comparing(scopeManager::getDependencyScopeWidth)
                        .reversed())
                .forEach(s -> {
                    System.out.println(s + " (width=" + scopeManager.getDependencyScopeWidth(s) + ")");
                    System.out.println("  Query : " + scopeManager.getPresence(s));
                    System.out.println("  Presence: "
                            + scopeManager.getBuildScopeSource().query(scopeManager.getPresence(s)).stream()
                                    .map(BuildScope::getId)
                                    .collect(Collectors.toSet()));
                    System.out.println("  Main project scope: "
                            + scopeManager
                                    .getDependencyScopeMainProjectBuildScope(s)
                                    .map(BuildScope::getId)
                                    .orElse("null"));
                });
    }

    private void dumpDependencyScopeDerives(ScopeManagerImpl scopeManager) {
        System.out.println(scopeManager.getId() + " defined dependency derive matrix:");
        ManagedScopeDeriver deriver = new ManagedScopeDeriver(scopeManager);
        scopeManager.getDependencyScopeUniverse().stream()
                .sorted(Comparator.comparing(scopeManager::getDependencyScopeWidth)
                        .reversed())
                .forEach(parent -> scopeManager.getDependencyScopeUniverse().stream()
                        .sorted(Comparator.comparing(scopeManager::getDependencyScopeWidth)
                                .reversed())
                        .forEach(child -> System.out.println(parent.getId() + " w/ child " + child.getId() + " -> "
                                + deriver.getDerivedScope(parent.getId(), child.getId()))));
    }

    private void dumpResolutionScopes(ScopeManagerImpl scopeManager) {
        System.out.println(scopeManager.getId() + " defined resolution scopes:");
        scopeManager.getResolutionScopeUniverse().stream()
                .sorted(Comparator.comparing(ResolutionScope::getId))
                .forEach(s -> {
                    System.out.println("* " + s.getId());
                    System.out.println("     Directly included: " + scopeManager.getDirectlyIncludedLabels(s));
                    System.out.println("     Directly excluded: " + scopeManager.getDirectlyExcludedLabels(s));
                    System.out.println(" Transitively excluded: " + scopeManager.getTransitivelyExcludedLabels(s));
                });
    }
}
