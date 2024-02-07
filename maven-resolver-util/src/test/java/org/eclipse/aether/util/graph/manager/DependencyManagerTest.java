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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.util.graph.SystemScopePredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT for {@link DependencyManager} implementations.
 */
public class DependencyManagerTest {

    @SuppressWarnings("deprecation")
    private static final SystemScopePredicate SYSTEM_PREDICATE = AbstractDependencyManager.SYSTEM_PREDICATE;

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
    void testClassic() {
        DependencyManager manager = new ClassicDependencyManager(SYSTEM_PREDICATE);
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNull(mngt);
    }

    @Test
    void testClassicTransitive() {
        DependencyManager manager = new ClassicDependencyManager(true, SYSTEM_PREDICATE);
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), E2.getVersion());
    }

    @Test
    void testTransitive() {
        DependencyManager manager = new TransitiveDependencyManager(SYSTEM_PREDICATE);
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
        assertEquals(B2.getVersion(), mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), E2.getVersion());
    }

    @Test
    void testDefault() {
        DependencyManager manager = new DefaultDependencyManager(SYSTEM_PREDICATE);
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
        assertEquals(mngt.getScope(), "newscope");
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
        assertEquals(B2.getVersion(), mngt.getVersion());
        mngt = manager.manageDependency(new Dependency(C1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
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
        assertEquals(mngt.getScope(), "newscope");
        mngt = manager.manageDependency(new Dependency(D1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getExclusions(), Collections.singleton(EXCLUSION));
        mngt = manager.manageDependency(new Dependency(E1, null));
        assertNotNull(mngt);
        assertEquals(mngt.getVersion(), E2.getVersion());
    }
}
