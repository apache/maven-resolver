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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT for {@link DependencyManager} implementations.
 */
public class DependencyManagerTest {

    private final Artifact A1 = new DefaultArtifact("test", "a", "", "1");

    private final Artifact A2 = new DefaultArtifact("test", "a", "", "2");

    private final Artifact B = new DefaultArtifact("test", "b", "", "");

    private final Artifact B1 = new DefaultArtifact("test", "b", "", "1");

    private final Artifact B2 = new DefaultArtifact("test", "b", "", "2");

    private final Artifact C1 = new DefaultArtifact("test", "c", "", "1");

    private final Artifact D1 = new DefaultArtifact("test", "d", "", "1");

    private final Artifact E1 = new DefaultArtifact("test", "e", "", "1");

    private final Artifact E2 = new DefaultArtifact("test", "e", "", "2");

    private final Exclusion EXCLUSION = new Exclusion("test", "excluded", null, null);

    private RepositorySystemSession session;

    private DependencyCollectionContext newContext(Dependency... managedDependencies) {
        return TestUtils.newCollectionContext(session, null, Arrays.asList(managedDependencies));
    }

    @BeforeEach
    void setUp() {
        session = TestUtils.newSession();
    }

    @Test
    void duplicateDepMgt() {
        DependencyManager manager = new TransitiveDependencyManager(null);
        DependencyManager derived = manager.deriveChildManager(newContext(
                        new Dependency(new DefaultArtifact("dupe:dupe:1.0"), ""),
                        new Dependency(new DefaultArtifact("dupe:dupe:2.0"), "")))
                .deriveChildManager(newContext());
        DependencyManagement management =
                derived.manageDependency(new Dependency(new DefaultArtifact("dupe:dupe:1.1"), ""));
        // bug: here would be 2.0
        assertEquals("1.0", management.getVersion());
    }

    @Test
    void testClassic() {
        DependencyManager manager = new ClassicDependencyManager(null);
        DependencyManagement mngt;

        // depth=1: only exclusion applied, nothing more
        manager = manager.deriveChildManager(newContext(
                new Dependency(A2, null, null),
                new Dependency(B, null, true),
                new Dependency(C1, "newscope", null),
                new Dependency(D1, null, null, Collections.singleton(EXCLUSION))));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=2: all applied (new ones ignored)
        manager = manager.deriveChildManager(newContext(new Dependency(B2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        assertNull(mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=3: all existing applied, new depMgt ignored, carried on only what we have so far
        manager = manager.deriveChildManager(newContext(new Dependency(E2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);
    }

    @Test
    void testTransitive() {
        DependencyManager manager = new TransitiveDependencyManager(null);
        DependencyManagement mngt;

        // depth=1: only exclusion applied, nothing more
        manager = manager.deriveChildManager(newContext(
                new Dependency(A2, null, null),
                new Dependency(B, null, true),
                new Dependency(C1, "newscope", null),
                new Dependency(D1, null, null, Collections.singleton(EXCLUSION))));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=2: all applied
        manager = manager.deriveChildManager(newContext(new Dependency(B2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        // DO NOT APPLY ONTO ITSELF
        assertNull(mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=3: all existing applied, new depMgt processed, carried on
        manager = manager.deriveChildManager(newContext(new Dependency(E2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        // DO NOT APPLY ONTO ITSELF
        assertNull(mngt);
    }

    /**
     * Verifies the instance-reuse optimization in {@link AbstractDependencyManager#deriveChildManager}:
     * when no new management data is collected and management is already being applied
     * (depth >= applyFrom), deriveChildManager should return the same instance.
     * This is critical for BF collector pool cache transparency (issue #2013).
     */
    @Test
    void testDeriveChildManagerReusesInstanceWhenNoNewManagementData() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // depth=0 → depth=1: root → first level (applyFrom=2, so not yet applied)
        DependencyManager depth1 = manager.deriveChildManager(newContext());
        assertNotSame(manager, depth1, "depth 0→1: must return new instance (not yet applied)");

        // depth=1 → depth=2: no managed deps, now at applyFrom=2 (applied)
        DependencyManager depth2 = depth1.deriveChildManager(newContext());
        assertNotSame(depth1, depth2, "depth 1→2: must return new instance (crossing applyFrom boundary)");

        // depth=2 → depth=3: no managed deps, already applied → should reuse
        DependencyManager depth3 = depth2.deriveChildManager(newContext());
        assertSame(depth2, depth3, "depth 2→3 with no managed deps: should reuse instance");

        // depth=3 → depth=4: still no managed deps → should keep reusing
        DependencyManager depth4 = depth3.deriveChildManager(newContext());
        assertSame(depth3, depth4, "depth 3→4 with no managed deps: should reuse instance");

        // depth=2 → depth=3 with managed deps: should NOT reuse
        DependencyManager depth3WithMgmt =
                depth2.deriveChildManager(newContext(new Dependency(new DefaultArtifact("new:dep:1.0"), "compile")));
        assertNotSame(depth2, depth3WithMgmt, "depth 2→3 with managed deps: must return new instance");
    }

    /**
     * Verifies the instance-reuse optimization with {@link DefaultDependencyManager} (applyFrom=0).
     * Since management is applied from depth 0, the optimization should fire at the very first
     * derivation when no management data is collected.
     */
    @Test
    void testDeriveChildManagerReusesInstanceWithDefaultManager() {
        DependencyManager manager = new DefaultDependencyManager(null);

        // DefaultDependencyManager has applyFrom=0, so isApplied() is true from the start.
        // depth=0 → depth=1: no managed deps → should reuse (already applied)
        DependencyManager depth1 = manager.deriveChildManager(newContext());
        assertSame(manager, depth1, "depth 0→1 with no managed deps: should reuse (applyFrom=0)");

        // depth=0 → depth=1 with managed deps: should NOT reuse
        DependencyManager depth1WithMgmt =
                manager.deriveChildManager(newContext(new Dependency(new DefaultArtifact("mgd:dep:1.0"), "compile")));
        assertNotSame(manager, depth1WithMgmt, "depth 0→1 with managed deps: must return new instance");

        // depth=1 (with mgmt) → depth=2: no managed deps but parent has management data,
        // so a new instance must be created to move the management data onto the parent chain
        // where getManagedVersion() can find it ("do not apply onto itself" semantics).
        DependencyManager depth2 = depth1WithMgmt.deriveChildManager(newContext());
        assertNotSame(depth1WithMgmt, depth2, "depth 1→2 with parent mgmt: must create new child");

        // depth=2 (no own mgmt) → depth=3: no managed deps → should reuse (no mgmt data)
        DependencyManager depth3 = depth2.deriveChildManager(newContext());
        assertSame(depth2, depth3, "depth 2→3 with no managed deps: should reuse");
    }

    /**
     * Verifies that the "nearer-to-root wins" invariant holds when the optimization
     * reuses instances. Scenario: root contributes version management at depth 1,
     * the optimization fires at depth 2→3 (empty context), and a deeper POM tries
     * to override the same key — the root's version must still win.
     */
    @Test
    void testNearerToRootWinsAfterOptimizationReusesInstance() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // depth=0 → depth=1: root contributes version 1.0 for artifact "x"
        DependencyManager depth1 =
                manager.deriveChildManager(newContext(new Dependency(new DefaultArtifact("test:x:1.0"), "compile")));

        // depth=1 → depth=2: empty context (new instance because applyFrom boundary)
        DependencyManager depth2 = depth1.deriveChildManager(newContext());
        assertNotSame(depth1, depth2, "depth 1→2: new instance (crossing applyFrom boundary)");

        // depth=2 → depth=3: empty context, optimization fires
        DependencyManager depth3 = depth2.deriveChildManager(newContext());
        assertSame(depth2, depth3, "depth 2→3: optimization should reuse instance");

        // Verify root's version still applies after optimization reuse
        DependencyManagement mngt = depth3.manageDependency(new Dependency(new DefaultArtifact("test:x:9.0"), null));
        assertNotNull(mngt, "management should apply at depth >= applyFrom");
        assertEquals("1.0", mngt.getVersion(), "root's version must apply after optimization reuse");

        // depth=3 → depth=4: context tries to override "x" with 2.0, but containsManagedVersion
        // finds it in ancestors → no new data collected → optimization fires again
        DependencyManager depth4 =
                depth3.deriveChildManager(newContext(new Dependency(new DefaultArtifact("test:x:2.0"), "compile")));
        assertSame(depth3, depth4, "optimization should fire when context only has already-managed keys");

        // Root's version still wins ("nearer-to-root wins" invariant)
        mngt = depth4.manageDependency(new Dependency(new DefaultArtifact("test:x:9.0"), null));
        assertNotNull(mngt, "management should still apply");
        assertEquals("1.0", mngt.getVersion(), "nearer-to-root version must win over deeper override attempt");
    }

    @Test
    void testDefault() {
        DependencyManager manager = new DefaultDependencyManager(null);
        DependencyManagement mngt;

        // depth=1: all applied
        manager = manager.deriveChildManager(newContext(
                new Dependency(A2, null, null),
                new Dependency(B, null, true),
                new Dependency(C1, "newscope", null),
                new Dependency(D1, null, null, Collections.singleton(EXCLUSION))));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        assertNull(mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=2: all applied
        manager = manager.deriveChildManager(newContext(new Dependency(B2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        // DO NOT APPLY ONTO ITSELF
        assertNull(mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);

        // depth=3: all existing applied, new depMgt processed, carried on
        manager = manager.deriveChildManager(newContext(new Dependency(E2, null, null)));
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), A2.getVersion());
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(Collections.singleton(EXCLUSION), mngt.getExclusions());
        mngt = manager.manageDependency(new Dependency(E1, null));
        // DO NOT APPLY ONTO ITSELF
        assertNull(mngt);
    }

    /**
     * Verifies the memoization cache in {@link AbstractDependencyManager#deriveChildManager}:
     * when the same managed dependencies list (same object reference) is passed multiple times,
     * the result must be identical (same object reference). This simulates the BFS collector
     * processing multiple siblings that share the same artifact descriptor — each call sees
     * the same interned managed deps list from the DataPool.
     */
    @Test
    void testDeriveChildManagerMemoizationOnListIdentity() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // Get to depth >= applyFrom (depth 2) so the optimization can fire
        manager =
                manager.deriveChildManager(newContext(new Dependency(new DefaultArtifact("root:dep:1.0"), "compile")));
        manager = manager.deriveChildManager(newContext());

        // Create a SINGLE managed deps list — simulates interned list from DataPool
        List<Dependency> sharedManagedDeps = Arrays.asList(
                new Dependency(new DefaultArtifact("mgd:a:1.0"), "compile"),
                new Dependency(new DefaultArtifact("mgd:b:2.0"), "runtime"));

        // First call: processes the list, caches the result
        DependencyCollectionContext ctx1 = TestUtils.newCollectionContext(session, null, sharedManagedDeps);
        DependencyManager result1 = manager.deriveChildManager(ctx1);

        // Second call with the SAME list object: should return the cached result
        DependencyCollectionContext ctx2 = TestUtils.newCollectionContext(session, null, sharedManagedDeps);
        DependencyManager result2 = manager.deriveChildManager(ctx2);

        assertSame(result1, result2, "memoization must return same instance for same list identity");

        // Third call with a DIFFERENT list object (same content): must NOT use cache
        // (identity mismatch — different object reference)
        List<Dependency> differentList = new ArrayList<>(sharedManagedDeps);
        DependencyCollectionContext ctx3 = TestUtils.newCollectionContext(session, null, differentList);
        DependencyManager result3 = manager.deriveChildManager(ctx3);

        // result3 should be equal to result1 (same content), but NOT necessarily the same object
        // (identity-based cache misses on different list objects)
        assertEquals(result1, result3, "same content should produce equal results");
    }

    /**
     * Verifies that the single-entry memoization cache correctly distinguishes between different
     * managed dependency lists. A cached result for list L1 must not be returned for list L2.
     * Since the cache is single-entry (last-seen), calling with L2 evicts L1.
     */
    @Test
    void testDeriveChildManagerMemoizationDistinguishesDifferentLists() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // Get to depth >= applyFrom
        manager =
                manager.deriveChildManager(newContext(new Dependency(new DefaultArtifact("root:dep:1.0"), "compile")));
        manager = manager.deriveChildManager(newContext());

        // Two different list objects with different content
        List<Dependency> list1 = Collections.singletonList(new Dependency(new DefaultArtifact("mgd:a:1.0"), "compile"));
        List<Dependency> list2 = Collections.singletonList(new Dependency(new DefaultArtifact("mgd:b:2.0"), "runtime"));

        DependencyManager result1 = manager.deriveChildManager(TestUtils.newCollectionContext(session, null, list1));
        DependencyManager result2 = manager.deriveChildManager(TestUtils.newCollectionContext(session, null, list2));

        // Different lists should produce different managers (different managed data)
        assertNotSame(result1, result2, "different lists must not share cached result");

        // Single-entry cache: last call was list2, so list2 should hit
        DependencyManager result2Again =
                manager.deriveChildManager(TestUtils.newCollectionContext(session, null, list2));
        assertSame(result2, result2Again, "list2 should return memoized result (last seen)");

        // list1 was evicted by list2, so calling with list1 recomputes (equal but not same object)
        DependencyManager result1Again =
                manager.deriveChildManager(TestUtils.newCollectionContext(session, null, list1));
        assertEquals(result1, result1Again, "list1 should produce equal result after eviction");
    }

    /**
     * Verifies that the memoization cache for empty managed deps lists works correctly.
     * This is the most common case (leaf artifacts without dependencyManagement).
     */
    @Test
    void testDeriveChildManagerMemoizationWithEmptyList() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // Get to depth >= applyFrom
        manager = manager.deriveChildManager(newContext());
        manager = manager.deriveChildManager(newContext());

        // Use the SAME empty list (e.g. Collections.emptyList() is a singleton)
        List<Dependency> emptyList = Collections.emptyList();

        DependencyManager result1 =
                manager.deriveChildManager(TestUtils.newCollectionContext(session, null, emptyList));
        DependencyManager result2 =
                manager.deriveChildManager(TestUtils.newCollectionContext(session, null, emptyList));

        // Both should return `this` (no new data + isApplied) and be memoized
        assertSame(manager, result1, "empty list should return this");
        assertSame(result1, result2, "memoization should return same instance for empty list");
    }
}
