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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class CycleRecordingDependencyVisitorTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/tree/").parseResource(resource);
    }

    private static String artifactId(DependencyNode node) {
        return node.getDependency().getArtifact().getArtifactId();
    }

    @Test
    void recordsBackEdgeAndDoesNotRecurseForever() throws Exception {
        DependencyNode root = parse("cycles.txt");
        StringBuilder trace = new StringBuilder();
        CycleRecordingDependencyVisitor visitor = new CycleRecordingDependencyVisitor(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                trace.append('>').append(artifactId(node)).append(' ');
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                trace.append('<').append(artifactId(node)).append(' ');
                return true;
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> root.accept(visitor));

        // The graph is a -> b -> c -> b plus a diamond d -> b; only c -> b is a back-edge.
        assertEquals(">a >b >c >b <b <c <b >d <d <a ", trace.toString());
        assertEquals(1, visitor.getCycles().size());
        List<DependencyNode> cycle = visitor.getCycles().get(0);
        assertEquals(3, cycle.size());
        assertEquals("b", artifactId(cycle.get(0)));
        assertEquals("c", artifactId(cycle.get(1)));
        assertSame(cycle.get(0), cycle.get(2));

        List<Map.Entry<DependencyNode, DependencyNode>> backEdges = visitor.getBackEdges();
        assertEquals(1, backEdges.size());
        assertSame(cycle.get(1), backEdges.get(0).getKey());
        assertSame(cycle.get(0), backEdges.get(0).getValue());
    }

    @Test
    void delegateVisitLeaveIsPairedWhenVisitEnterPrunes() throws Exception {
        DependencyNode root = parse("cycles.txt");
        StringBuilder trace = new StringBuilder();
        CycleRecordingDependencyVisitor visitor = new CycleRecordingDependencyVisitor(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                trace.append('>').append(artifactId(node)).append(' ');
                return !"b".equals(artifactId(node));
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                trace.append('<').append(artifactId(node)).append(' ');
                return true;
            }
        });

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> root.accept(visitor));

        assertEquals(">a >b <b >d <d <a ", trace.toString());
    }

    @Test
    void recordsCyclesWithoutDelegate() throws Exception {
        DependencyNode root = parse("cycles.txt");
        CycleRecordingDependencyVisitor visitor = new CycleRecordingDependencyVisitor();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> root.accept(visitor));

        assertEquals(1, visitor.getCycles().size());
        assertEquals(1, visitor.getBackEdges().size());
    }
}
