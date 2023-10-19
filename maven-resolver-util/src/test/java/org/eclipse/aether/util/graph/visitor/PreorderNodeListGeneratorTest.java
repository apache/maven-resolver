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
package org.eclipse.aether.util.graph.visitor;

import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PreorderNodeListGeneratorTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/ordered-list/").parseResource(resource);
    }

    private void assertSequence(List<DependencyNode> actual, String... expected) {
        assertEquals(expected.length, actual.size(), actual.toString());
        for (int i = 0; i < expected.length; i++) {
            DependencyNode node = actual.get(i);
            assertEquals(expected[i], node.getDependency().getArtifact().getArtifactId(), actual.toString());
        }
    }

    @Test
    public void testOrdering() throws Exception {
        DependencyNode root = parse("simple.txt");

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept(visitor);

        assertSequence(visitor.getNodes(), "a", "b", "c", "d", "e");
    }

    @Test
    public void testDuplicateSuppression() throws Exception {
        DependencyNode root = parse("cycles.txt");

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept(visitor);

        assertSequence(visitor.getNodes(), "a", "b", "c", "d", "e");
    }
}
