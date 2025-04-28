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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ClassicDependencyManagerTest {

    private static final Artifact A = new DefaultArtifact("test", "a", "", "");

    private static final Artifact A1 = new DefaultArtifact("test", "a", "", "1");

    private static final Artifact B = new DefaultArtifact("test", "b", "", "");

    private static final Artifact B1 = new DefaultArtifact("test", "b", "", "1");

    private RepositorySystemSession session;

    private DependencyCollectionContext newContext(Dependency... managedDependencies) {
        return TestUtils.newCollectionContext(session, null, Arrays.asList(managedDependencies));
    }

    @Before
    public void setUp() {
        session = TestUtils.newSession();
    }

    @Test
    public void testManageOptional() {
        DependencyManager manager = new ClassicDependencyManager();

        manager = manager.deriveChildManager(newContext(new Dependency(A, null, null), new Dependency(B, null, true)));
        DependencyManagement mngt;
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNull(mngt);

        manager = manager.deriveChildManager(newContext());
        mngt = manager.manageDependency(new Dependency(A1, null));
        assertNull(mngt);
        mngt = manager.manageDependency(new Dependency(B1, null));
        assertNotNull(mngt);
        assertEquals(Boolean.TRUE, mngt.getOptional());
    }
}
