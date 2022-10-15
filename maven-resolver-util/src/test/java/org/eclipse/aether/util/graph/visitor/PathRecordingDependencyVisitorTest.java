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

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

import java.util.List;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class PathRecordingDependencyVisitorTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/path-recorder/").parseResource(resource);
    }

    private void assertPath(List<DependencyNode> actual, String... expected) {
        assertEquals(actual.toString(), expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            DependencyNode node = actual.get(i);
            assertEquals(
                    actual.toString(),
                    expected[i],
                    node.getDependency().getArtifact().getArtifactId());
        }
    }

    @Test
    public void testGetPaths_RecordsMatchesBeneathUnmatchedParents() throws Exception {
        DependencyNode root = parse("simple.txt");

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(new ArtifactMatcher());
        root.accept(visitor);

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals(paths.toString(), 2, paths.size());
        assertPath(paths.get(0), "a", "b", "x");
        assertPath(paths.get(1), "a", "x");
    }

    @Test
    public void testGetPaths_DoesNotRecordMatchesBeneathMatchedParents() throws Exception {
        DependencyNode root = parse("nested.txt");

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(new ArtifactMatcher());
        root.accept(visitor);

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals(paths.toString(), 1, paths.size());
        assertPath(paths.get(0), "x");
    }

    @Test
    public void testGetPaths_RecordsMatchesBeneathMatchedParentsIfRequested() throws Exception {
        DependencyNode root = parse("nested.txt");

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(new ArtifactMatcher(), false);
        root.accept(visitor);

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals(paths.toString(), 3, paths.size());
        assertPath(paths.get(0), "x");
        assertPath(paths.get(1), "x", "a", "y");
        assertPath(paths.get(2), "x", "y");
    }

    @Test
    public void testFilterCalledWithProperParentStack() throws Exception {
        DependencyNode root = parse("parents.txt");

        final StringBuilder buffer = new StringBuilder(256);
        DependencyFilter filter = new DependencyFilter() {
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                requireNonNull(node, "node cannot be null");
                requireNonNull(parents, "parents cannot be null");
                for (DependencyNode parent : parents) {
                    buffer.append(parent.getDependency().getArtifact().getArtifactId());
                }
                buffer.append(",");
                return false;
            }
        };

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(filter);
        root.accept(visitor);

        assertEquals(",a,ba,cba,a,ea,", buffer.toString());
    }

    @Test
    public void testGetPaths_HandlesCycles() throws Exception {
        DependencyNode root = parse("cycle.txt");

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(new ArtifactMatcher(), false);
        root.accept(visitor);

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals(paths.toString(), 4, paths.size());
        assertPath(paths.get(0), "a", "b", "x");
        assertPath(paths.get(1), "a", "x");
        assertPath(paths.get(2), "a", "x", "b", "x");
        assertPath(paths.get(3), "a", "x", "x");
    }

    @Test
    public void testGetPaths_HandlesCycles_threePaths() throws Exception {
        DependencyNode root = parse("cycle-3paths.txt");

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(new ArtifactMatcher());
        root.accept(visitor);

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals(paths.toString(), 1, paths.size());
        assertPath(paths.get(0), "a", "b");
    }

    private static class ArtifactMatcher implements DependencyFilter {
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            return node.getDependency() != null
                    && node.getDependency().getArtifact().getGroupId().equals("match");
        }
    }
}
