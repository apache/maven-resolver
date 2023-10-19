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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class AndDependencyTraverserTest {

    static class DummyDependencyTraverser implements DependencyTraverser {

        private final boolean traverse;

        private final DependencyTraverser child;

        public DummyDependencyTraverser() {
            this(true);
        }

        public DummyDependencyTraverser(boolean traverse) {
            this.traverse = traverse;
            this.child = this;
        }

        public DummyDependencyTraverser(boolean traverse, DependencyTraverser child) {
            this.traverse = traverse;
            this.child = child;
        }

        public boolean traverseDependency(Dependency dependency) {
            requireNonNull(dependency, "dependency cannot be null");
            return traverse;
        }

        public DependencyTraverser deriveChildTraverser(DependencyCollectionContext context) {
            requireNonNull(context, "context cannot be null");
            return child;
        }
    }

    private RepositorySystemSession session;
    private DependencyCollectionContext context;

    @BeforeEach
    public void setup() {
        session = TestUtils.newSession();
        context = TestUtils.newCollectionContext(session, null, Collections.emptyList());
    }

    @AfterEach
    public void teardown() throws Exception {
        if (session.getLocalRepository() != null) {
            TestFileUtils.deleteFile(session.getLocalRepository().getBasedir());
        }
        session = null;
        context = null;
    }

    @Test
    public void testNewInstance() {
        assertNull(AndDependencyTraverser.newInstance(null, null));
        DependencyTraverser traverser = new DummyDependencyTraverser();
        assertSame(traverser, AndDependencyTraverser.newInstance(traverser, null));
        assertSame(traverser, AndDependencyTraverser.newInstance(null, traverser));
        assertSame(traverser, AndDependencyTraverser.newInstance(traverser, traverser));
        assertNotNull(AndDependencyTraverser.newInstance(traverser, new DummyDependencyTraverser()));
    }

    @Test
    public void testTraverseDependency() {
        Dependency dependency = new Dependency(new DefaultArtifact("g:a:v:1"), "runtime");

        DependencyTraverser traverser = new AndDependencyTraverser();
        assertTrue(traverser.traverseDependency(dependency));

        traverser =
                new AndDependencyTraverser(new DummyDependencyTraverser(false), new DummyDependencyTraverser(false));
        assertFalse(traverser.traverseDependency(dependency));

        traverser = new AndDependencyTraverser(new DummyDependencyTraverser(true), new DummyDependencyTraverser(false));
        assertFalse(traverser.traverseDependency(dependency));

        traverser = new AndDependencyTraverser(new DummyDependencyTraverser(true), new DummyDependencyTraverser(true));
        assertTrue(traverser.traverseDependency(dependency));
    }

    @Test
    public void testDeriveChildTraverser_Unchanged() {
        DependencyTraverser other1 = new DummyDependencyTraverser(true);
        DependencyTraverser other2 = new DummyDependencyTraverser(false);
        DependencyTraverser traverser = new AndDependencyTraverser(other1, other2);
        assertSame(traverser, traverser.deriveChildTraverser(context));
    }

    @Test
    public void testDeriveChildTraverser_OneRemaining() {
        DependencyTraverser other1 = new DummyDependencyTraverser(true);
        DependencyTraverser other2 = new DummyDependencyTraverser(false, null);
        DependencyTraverser traverser = new AndDependencyTraverser(other1, other2);
        assertSame(other1, traverser.deriveChildTraverser(context));
    }

    @Test
    public void testDeriveChildTraverser_ZeroRemaining() {
        DependencyTraverser other1 = new DummyDependencyTraverser(true, null);
        DependencyTraverser other2 = new DummyDependencyTraverser(false, null);
        DependencyTraverser traverser = new AndDependencyTraverser(other1, other2);
        assertNull(traverser.deriveChildTraverser(context));
    }

    @Test
    public void testEquals() {
        DependencyTraverser other1 = new DummyDependencyTraverser(true);
        DependencyTraverser other2 = new DummyDependencyTraverser(false);
        DependencyTraverser traverser1 = new AndDependencyTraverser(other1, other2);
        DependencyTraverser traverser2 = new AndDependencyTraverser(other2, other1);
        DependencyTraverser traverser3 = new AndDependencyTraverser(other1);
        assertEquals(traverser1, traverser1);
        assertEquals(traverser1, traverser2);
        assertNotEquals(traverser1, traverser3);
        assertNotEquals(traverser1, this);
        assertNotEquals(traverser1, null);
    }

    @Test
    public void testHashCode() {
        DependencyTraverser other1 = new DummyDependencyTraverser(true);
        DependencyTraverser other2 = new DummyDependencyTraverser(false);
        DependencyTraverser traverser1 = new AndDependencyTraverser(other1, other2);
        DependencyTraverser traverser2 = new AndDependencyTraverser(other2, other1);
        assertEquals(traverser1.hashCode(), traverser2.hashCode());
    }
}
