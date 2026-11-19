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

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Depth semantics of {@link ScopeDependencySelector}: the selector in the session sits "above" the root, every
 * {@link DependencySelector#deriveChildSelector} call moves one level down, and the configured range decides at
 * which levels the scope filter applies.
 */
class ScopeDependencySelectorTest {
    private final RepositorySystemSession session = TestUtils.newSession();
    private final Dependency compile = new Dependency(new DefaultArtifact("g:compile:1"), "compile");
    private final Dependency runtime = new Dependency(new DefaultArtifact("g:runtime:1"), "runtime");
    private final Dependency test = new Dependency(new DefaultArtifact("g:test:1"), "test");
    private final Dependency root = new Dependency(new DefaultArtifact("g:root:1"), "");

    private DependencySelector child(DependencySelector selector, Dependency dependency) {
        return selector.deriveChildSelector(
                TestUtils.newCollectionContext(session, dependency, Collections.emptyList()));
    }

    @Test
    void fromDirectKeepsDirectDependenciesOfAnyScope() {
        DependencySelector selector = ScopeDependencySelector.fromDirect(null, singleton("test"));
        assertTrue(selector.selectDependency(test), "nothing is filtered above the root");

        DependencySelector direct = child(selector, null);
        assertTrue(direct.selectDependency(test), "direct dependencies keep every scope");
        assertTrue(direct.selectDependency(compile));

        DependencySelector transitive = child(direct, compile);
        assertFalse(transitive.selectDependency(test), "transitive test scope is filtered");
        assertTrue(transitive.selectDependency(compile));
        assertSame(transitive, child(transitive, compile), "once applied with no upper bound the selector is stable");
    }

    @Test
    void fromRootFiltersDirectDependencies() {
        DependencySelector selector = ScopeDependencySelector.fromRoot(null, singleton("test"));
        assertTrue(selector.selectDependency(test), "nothing is filtered above the root");

        DependencySelector direct = child(selector, null);
        assertFalse(direct.selectDependency(test));
        assertTrue(direct.selectDependency(compile));
    }

    @Test
    void includedScopesAreAnAllowList() {
        DependencySelector direct =
                child(ScopeDependencySelector.fromRoot(Arrays.asList("compile", "runtime"), null), null);
        assertTrue(direct.selectDependency(compile));
        assertTrue(direct.selectDependency(runtime));
        assertFalse(direct.selectDependency(test));
    }

    @Test
    void excludedScopeWinsOverIncludedScope() {
        DependencySelector direct =
                child(ScopeDependencySelector.fromRoot(singleton("compile"), singleton("compile")), null);
        assertFalse(direct.selectDependency(compile));
    }

    @Test
    void fromToStopsApplyingBeyondTheUpperDepth() {
        DependencySelector selector = ScopeDependencySelector.fromTo(2, 2, null, singleton("test"));
        DependencySelector depth1 = child(selector, null);
        DependencySelector depth2 = child(depth1, compile);
        DependencySelector depth3 = child(depth2, compile);
        assertTrue(depth1.selectDependency(test));
        assertFalse(depth2.selectDependency(test));
        assertTrue(depth3.selectDependency(test), "beyond applyTo the filter no longer applies");
    }

    @Test
    void legacyShiftsOneLevelWhenCollectionStartsFromAnArtifact() {
        DependencySelector selector = ScopeDependencySelector.legacy(null, singleton("test"));
        DependencySelector direct = child(selector, null);
        assertTrue(
                direct.selectDependency(test), "Resolver 1.x kept direct dependencies when a root artifact was given");
        DependencySelector transitive = child(direct, compile);
        assertFalse(transitive.selectDependency(test));
    }

    @Test
    void legacyAppliesAtOnceWhenCollectionStartsFromADependency() {
        DependencySelector selector = ScopeDependencySelector.legacy(null, singleton("test"));
        DependencySelector direct = child(selector, root);
        assertFalse(
                direct.selectDependency(test), "Resolver 1.x filtered from the first level without a root artifact");
    }

    @Test
    void rejectsInvalidDepths() {
        assertThrows(IllegalArgumentException.class, () -> ScopeDependencySelector.from(0, null, null));
        assertThrows(IllegalArgumentException.class, () -> ScopeDependencySelector.fromTo(3, 2, null, null));
    }

    @Test
    void equalityFactorsInTheStartingConfiguration() {
        assertEquals(
                ScopeDependencySelector.fromRoot(null, singleton("test")),
                ScopeDependencySelector.fromRoot(null, singleton("test")));
        assertEquals(
                ScopeDependencySelector.fromRoot(null, singleton("test")).hashCode(),
                ScopeDependencySelector.fromRoot(null, singleton("test")).hashCode());
        assertNotEquals(
                ScopeDependencySelector.fromRoot(null, singleton("test")),
                ScopeDependencySelector.fromDirect(null, singleton("test")));
        // same depth, same range, same scopes, but one of them got there by the legacy shift
        assertNotEquals(
                child(ScopeDependencySelector.legacy(null, singleton("test")), null),
                child(ScopeDependencySelector.fromDirect(null, singleton("test")), null));
    }
}
