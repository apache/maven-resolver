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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Depth semantics of {@link OptionalDependencySelector}, and the two session-data hooks that let a caller exempt
 * artifacts from the filter and learn which optional dependencies it dropped.
 */
class OptionalDependencySelectorTest {
    private final DefaultRepositorySystemSession session = TestUtils.newSession();
    private final Dependency optional = new Dependency(new DefaultArtifact("g:optional:1"), "compile", true);
    private final Dependency otherOptional = new Dependency(new DefaultArtifact("g:other:1"), "compile", true);
    private final Dependency mandatory = new Dependency(new DefaultArtifact("g:mandatory:1"), "compile", false);

    private DependencySelector child(DependencySelector selector, Dependency dependency) {
        return selector.deriveChildSelector(
                TestUtils.newCollectionContext(session, dependency, Collections.emptyList()));
    }

    @Test
    void fromDirectKeepsDirectOptionalDependencies() {
        DependencySelector selector = OptionalDependencySelector.fromDirect();
        assertTrue(selector.selectDependency(optional), "nothing is filtered above the root");

        DependencySelector direct = child(selector, null);
        assertTrue(direct.selectDependency(optional), "direct optional dependencies are kept");

        DependencySelector transitive = child(direct, mandatory);
        assertFalse(transitive.selectDependency(optional), "transitive optional dependencies are dropped");
        assertTrue(transitive.selectDependency(mandatory));
        assertSame(transitive, child(transitive, mandatory), "once applied the selector is stable");
    }

    @Test
    void fromRootDropsOptionalDependenciesAtOnce() {
        DependencySelector direct = child(OptionalDependencySelector.fromRoot(), null);
        assertFalse(direct.selectDependency(optional));
        assertTrue(direct.selectDependency(mandatory));
    }

    @Test
    void rejectsZeroDepth() {
        assertThrows(IllegalArgumentException.class, () -> OptionalDependencySelector.from(0));
    }

    @Test
    void ignoredKeysExemptAnArtifactFromTheFilter() {
        session.getData()
                .set(
                        OptionalDependencySelector.IGNORED_KEYS,
                        new HashSet<>(singleton(ArtifactIdUtils.toId(optional.getArtifact()))));
        DependencySelector transitive = child(child(OptionalDependencySelector.fromDirect(), null), mandatory);
        assertTrue(transitive.selectDependency(optional), "listed artifact passes although optional");
        assertFalse(transitive.selectDependency(otherOptional), "unlisted optional artifact is still dropped");
    }

    @Test
    void unselectedKeysRecordTheDroppedOptionalDependencies() {
        Set<String> unselected = new HashSet<>();
        session.getData().set(OptionalDependencySelector.UNSELECTED_KEYS, unselected);
        DependencySelector transitive = child(child(OptionalDependencySelector.fromDirect(), null), mandatory);
        assertFalse(transitive.selectDependency(optional));
        assertTrue(transitive.selectDependency(mandatory));
        assertEquals(singleton(ArtifactIdUtils.toId(optional.getArtifact())), unselected);
    }

    @Test
    void equalityFactorsInTheStartingConfiguration() {
        assertEquals(OptionalDependencySelector.fromRoot(), OptionalDependencySelector.fromRoot());
        assertEquals(
                OptionalDependencySelector.fromRoot().hashCode(),
                OptionalDependencySelector.fromRoot().hashCode());
        assertNotEquals(OptionalDependencySelector.fromRoot(), OptionalDependencySelector.fromDirect());
        assertNotEquals(
                child(OptionalDependencySelector.fromDirect(), null),
                OptionalDependencySelector.fromDirect(),
                "a derived selector is one level deeper");
    }
}
