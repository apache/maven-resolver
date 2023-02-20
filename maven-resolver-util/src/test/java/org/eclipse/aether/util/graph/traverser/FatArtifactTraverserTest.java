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
package org.eclipse.aether.util.graph.traverser;

import java.util.Collections;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FatArtifactTraverserTest {

    private RepositorySystemSession session;
    private DependencyCollectionContext context;

    @Before
    public void setup() {
        session = TestUtils.newSession();
        context = TestUtils.newCollectionContext(session, null, Collections.emptyList());
    }

    @After
    public void teardown() throws Exception {
        if (session.getLocalRepository() != null) {
            TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
        }
        session = null;
        context = null;
    }

    @Test
    public void testTraverseDependency() {
        DependencyTraverser traverser = new FatArtifactTraverser();
        Map<String, String> props = null;
        assertTrue(traverser.traverseDependency(new Dependency(new DefaultArtifact("g:a:v:1", props), "test")));
        props = Collections.singletonMap(ArtifactProperties.INCLUDES_DEPENDENCIES, "false");
        assertTrue(traverser.traverseDependency(new Dependency(new DefaultArtifact("g:a:v:1", props), "test")));
        props = Collections.singletonMap(ArtifactProperties.INCLUDES_DEPENDENCIES, "unrecognized");
        assertTrue(traverser.traverseDependency(new Dependency(new DefaultArtifact("g:a:v:1", props), "test")));
        props = Collections.singletonMap(ArtifactProperties.INCLUDES_DEPENDENCIES, "true");
        assertFalse(traverser.traverseDependency(new Dependency(new DefaultArtifact("g:a:v:1", props), "test")));
    }

    @Test
    public void testDeriveChildTraverser() {
        DependencyTraverser traverser = new FatArtifactTraverser();
        assertSame(traverser, traverser.deriveChildTraverser(context));
    }

    @Test
    public void testEquals() {
        DependencyTraverser traverser1 = new FatArtifactTraverser();
        DependencyTraverser traverser2 = new FatArtifactTraverser();
        assertEquals(traverser1, traverser1);
        assertEquals(traverser1, traverser2);
        assertNotEquals(traverser1, this);
        assertNotEquals(traverser1, null);
    }

    @Test
    public void testHashCode() {
        DependencyTraverser traverser1 = new FatArtifactTraverser();
        DependencyTraverser traverser2 = new FatArtifactTraverser();
        assertEquals(traverser1.hashCode(), traverser2.hashCode());
    }
}
