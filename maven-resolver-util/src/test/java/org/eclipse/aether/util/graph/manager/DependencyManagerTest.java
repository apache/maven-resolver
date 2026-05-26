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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManagement.Subject;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
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
     * Tests that root-level management produces enforced results for version, scope, and optional.
     */
    @Test
    void testTransitiveEnforcementFromRoot() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // depth=1: derive from root with managed dependencies
        manager = manager.deriveChildManager(newContext(
                new Dependency(A2, null, null), new Dependency(B, null, true), new Dependency(C1, "newscope", null)));

        // depth=2: management is applied — check enforcement flags
        manager = manager.deriveChildManager(newContext());
        DependencyManagement mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(A2.getVersion(), mngt.getVersion());
        // version management from root should be enforced
        assertTrue(mngt.isManagedSubject(Subject.VERSION));
        assertTrue(mngt.isManagedSubjectEnforced(Subject.VERSION));

        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        // scope management from root should be enforced
        assertTrue(mngt.isManagedSubject(Subject.SCOPE));
        assertTrue(mngt.isManagedSubjectEnforced(Subject.SCOPE));

        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        // optional management from root should be enforced
        assertTrue(mngt.isManagedSubject(Subject.OPTIONAL));
        assertTrue(mngt.isManagedSubjectEnforced(Subject.OPTIONAL));
    }

    /**
     * Tests that transitive (non-root) management produces advised (not enforced) results
     * for scope and optional.
     */
    @Test
    void testTransitiveEnforcementFromNonRoot() {
        DependencyManager manager = new TransitiveDependencyManager(null);

        // depth=1: no managed dependencies at root level
        manager = manager.deriveChildManager(newContext());

        // depth=2: managed dependencies introduced at transitive level
        manager = manager.deriveChildManager(newContext(
                new Dependency(A2, null, null), new Dependency(B, null, true), new Dependency(C1, "newscope", null)));

        // depth=3: management is applied — check enforcement flags
        manager = manager.deriveChildManager(newContext());
        DependencyManagement mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertEquals(A2.getVersion(), mngt.getVersion());
        // version management from non-root should NOT be enforced (advised)
        assertTrue(mngt.isManagedSubject(Subject.VERSION));
        assertFalse(mngt.isManagedSubjectEnforced(Subject.VERSION));

        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals("newscope", mngt.getScope());
        // scope management from non-root should NOT be enforced (advised)
        assertTrue(mngt.isManagedSubject(Subject.SCOPE));
        assertFalse(mngt.isManagedSubjectEnforced(Subject.SCOPE));

        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
        // optional management from non-root should NOT be enforced (advised)
        assertTrue(mngt.isManagedSubject(Subject.OPTIONAL));
        assertFalse(mngt.isManagedSubjectEnforced(Subject.OPTIONAL));
    }

    /**
     * Tests that classic dependency manager also produces enforced results from root.
     */
    @Test
    void testClassicEnforcementFromRoot() {
        DependencyManager manager = new ClassicDependencyManager(null);

        // depth=1: derive from root
        manager = manager.deriveChildManager(
                newContext(new Dependency(A2, null, null), new Dependency(C1, "newscope", null)));

        // depth=2: management is applied
        manager = manager.deriveChildManager(newContext());
        DependencyManagement mngt = manager.manageDependency(new Dependency(A1, null));
        assertNotNull(mngt);
        assertTrue(mngt.isManagedSubjectEnforced(Subject.VERSION));

        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertTrue(mngt.isManagedSubjectEnforced(Subject.SCOPE));
    }

    /**
     * Tests backwards compatibility: setManagedBits maps to isManagedSubject/isManagedSubjectEnforced.
     */
    @Test
    void testSetManagedBitsBackwardsCompat() {
        DefaultDependencyNode node = new DefaultDependencyNode(new Dependency(A1, "compile"));

        // Set via deprecated API
        node.setManagedBits(DependencyNode.MANAGED_VERSION | DependencyNode.MANAGED_SCOPE);

        // Check via new API — all mapped as enforced
        assertTrue(node.isManagedSubject(Subject.VERSION));
        assertTrue(node.isManagedSubjectEnforced(Subject.VERSION));
        assertTrue(node.isManagedSubject(Subject.SCOPE));
        assertTrue(node.isManagedSubjectEnforced(Subject.SCOPE));

        // Unset subjects
        assertFalse(node.isManagedSubject(Subject.OPTIONAL));
        assertFalse(node.isManagedSubjectEnforced(Subject.OPTIONAL));
        assertFalse(node.isManagedSubject(Subject.PROPERTIES));
        assertFalse(node.isManagedSubject(Subject.EXCLUSIONS));

        // Round-trip: getManagedBits still works
        assertEquals(DependencyNode.MANAGED_VERSION | DependencyNode.MANAGED_SCOPE, node.getManagedBits());
    }

    /**
     * Tests that setManagedSubjects with explicit enforcement flags works correctly.
     */
    @Test
    void testSetManagedSubjectsWithEnforcement() {
        DefaultDependencyNode node = new DefaultDependencyNode(new Dependency(A1, "compile"));

        EnumMap<Subject, Boolean> subjects = new EnumMap<>(Subject.class);
        subjects.put(Subject.VERSION, true); // enforced
        subjects.put(Subject.SCOPE, false); // advised
        subjects.put(Subject.OPTIONAL, false); // advised
        node.setManagedSubjects(subjects);

        // All are managed
        assertTrue(node.isManagedSubject(Subject.VERSION));
        assertTrue(node.isManagedSubject(Subject.SCOPE));
        assertTrue(node.isManagedSubject(Subject.OPTIONAL));
        assertFalse(node.isManagedSubject(Subject.PROPERTIES));

        // Only version is enforced
        assertTrue(node.isManagedSubjectEnforced(Subject.VERSION));
        assertFalse(node.isManagedSubjectEnforced(Subject.SCOPE));
        assertFalse(node.isManagedSubjectEnforced(Subject.OPTIONAL));
        assertFalse(node.isManagedSubjectEnforced(Subject.PROPERTIES));

        // getManagedBits still reports all managed subjects (regardless of enforcement)
        assertEquals(
                DependencyNode.MANAGED_VERSION | DependencyNode.MANAGED_SCOPE | DependencyNode.MANAGED_OPTIONAL,
                node.getManagedBits());
    }

    /**
     * Tests that setManagedSubjects with null clears all managed subjects.
     */
    @Test
    void testSetManagedSubjectsNull() {
        DefaultDependencyNode node = new DefaultDependencyNode(new Dependency(A1, "compile"));

        EnumMap<Subject, Boolean> subjects = new EnumMap<>(Subject.class);
        subjects.put(Subject.VERSION, true);
        node.setManagedSubjects(subjects);
        assertTrue(node.isManagedSubject(Subject.VERSION));

        // Clear
        node.setManagedSubjects(null);
        assertFalse(node.isManagedSubject(Subject.VERSION));
        assertEquals(0, node.getManagedBits());
    }
}
