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
package org.eclipse.aether.util.graph.selector;

import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.Test;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class AndDependencySelectorTest {

    static class DummyDependencySelector implements DependencySelector {

        private final boolean select;

        private final DependencySelector child;

        public DummyDependencySelector() {
            this(true);
        }

        public DummyDependencySelector(boolean select) {
            this.select = select;
            this.child = this;
        }

        public DummyDependencySelector(boolean select, DependencySelector child) {
            this.select = select;
            this.child = child;
        }

        public boolean selectDependency(Dependency dependency) {
            requireNonNull(dependency, "dependency cannot be null");
            return select;
        }

        public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
            requireNonNull(context, "context cannot be null");
            return child;
        }

        @Override
        public String toString() {
            return "Dummy(" + select + ')';
        }
    }

    @Test
    void testNewInstance() {
        assertNull(AndDependencySelector.newInstance(null, null));
        DependencySelector selector = new DummyDependencySelector();
        assertSame(selector, AndDependencySelector.newInstance(selector, null));
        assertSame(selector, AndDependencySelector.newInstance(null, selector));
        assertSame(selector, AndDependencySelector.newInstance(selector, selector));
        assertNotNull(AndDependencySelector.newInstance(selector, new DummyDependencySelector()));
    }

    @Test
    void testTraverseDependency() {
        Dependency dependency = new Dependency(new DefaultArtifact("g:a:v:1"), "runtime");

        DependencySelector selector = new AndDependencySelector();
        assertTrue(selector.selectDependency(dependency));

        selector = new AndDependencySelector(new DummyDependencySelector(false), new DummyDependencySelector(false));
        assertFalse(selector.selectDependency(dependency));

        selector = new AndDependencySelector(new DummyDependencySelector(true), new DummyDependencySelector(false));
        assertFalse(selector.selectDependency(dependency));

        selector = new AndDependencySelector(new DummyDependencySelector(true), new DummyDependencySelector(true));
        assertTrue(selector.selectDependency(dependency));
    }

    @Test
    void testDeriveChildSelector_Unchanged() {
        DependencySelector other1 = new DummyDependencySelector(true);
        DependencySelector other2 = new DummyDependencySelector(false);
        DependencySelector selector = new AndDependencySelector(other1, other2);
        RepositorySystemSession session = TestUtils.newSession();
        DependencyCollectionContext context = TestUtils.newCollectionContext(session, null, Collections.emptyList());
        assertSame(selector, selector.deriveChildSelector(context));
    }

    @Test
    void testDeriveChildSelector_OneRemaining() {
        DependencySelector other1 = new DummyDependencySelector(true);
        DependencySelector other2 = new DummyDependencySelector(false, null);
        DependencySelector selector = new AndDependencySelector(other1, other2);
        RepositorySystemSession session = TestUtils.newSession();
        DependencyCollectionContext context = TestUtils.newCollectionContext(session, null, Collections.emptyList());
        assertSame(other1, selector.deriveChildSelector(context));
    }

    @Test
    void testDeriveChildSelector_ZeroRemaining() {
        DependencySelector other1 = new DummyDependencySelector(true, null);
        DependencySelector other2 = new DummyDependencySelector(false, null);
        DependencySelector selector = new AndDependencySelector(other1, other2);
        RepositorySystemSession session = TestUtils.newSession();
        DependencyCollectionContext context = TestUtils.newCollectionContext(session, null, Collections.emptyList());
        assertNull(selector.deriveChildSelector(context));
    }

    @Test
    void testEquals() {
        DependencySelector other1 = new DummyDependencySelector(true);
        DependencySelector other2 = new DummyDependencySelector(false);
        DependencySelector selector1 = new AndDependencySelector(other1, other2);
        DependencySelector selector2 = new AndDependencySelector(other2, other1);
        DependencySelector selector3 = new AndDependencySelector(other1);
        assertEquals(selector1, selector1);
        assertEquals(selector1, selector2);
        assertNotEquals(selector1, selector3);
        assertNotEquals(selector1, this);
        assertNotEquals(selector1, null);
    }

    @Test
    void testHashCode() {
        DependencySelector other1 = new DummyDependencySelector(true);
        DependencySelector other2 = new DummyDependencySelector(false);
        DependencySelector selector1 = new AndDependencySelector(other1, other2);
        DependencySelector selector2 = new AndDependencySelector(other2, other1);
        assertEquals(selector1.hashCode(), selector2.hashCode());
    }

    @Test
    void testToString() {
        DependencySelector andSelector =
                new AndDependencySelector(new DummyDependencySelector(true), new DummyDependencySelector(false));
        assertEquals("AndDependencySelector(Dummy(true) && Dummy(false))", andSelector.toString());
    }
}
