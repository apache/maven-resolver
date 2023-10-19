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
package org.eclipse.aether.util.graph.transformer;

import java.util.List;

import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class NearestVersionSelectorTest extends AbstractDependencyGraphTransformerTest {

    @Override
    protected ConflictResolver newTransformer() {
        return new ConflictResolver(
                new NearestVersionSelector(), new JavaScopeSelector(),
                new SimpleOptionalitySelector(), new JavaScopeDeriver());
    }

    @Override
    protected DependencyGraphParser newParser() {
        return new DependencyGraphParser("transformer/version-resolver/");
    }

    @Test
    public void testSelectHighestVersionFromMultipleVersionsAtSameLevel() throws Exception {
        DependencyNode root = parseResource("sibling-versions.txt");
        assertSame(root, transform(root));

        assertEquals(1, root.getChildren().size());
        assertEquals("3", root.getChildren().get(0).getArtifact().getVersion());
    }

    @Test
    public void testSelectedVersionAtDeeperLevelThanOriginallySeen() throws Exception {
        DependencyNode root = parseResource("nearest-underneath-loser-a.txt");

        assertSame(root, transform(root));

        List<DependencyNode> trail = find(root, "j");
        assertEquals(5, trail.size());
    }

    @Test
    public void testNearestDirtyVersionUnderneathRemovedNode() throws Exception {
        DependencyNode root = parseResource("nearest-underneath-loser-b.txt");

        assertSame(root, transform(root));

        List<DependencyNode> trail = find(root, "j");
        assertEquals(5, trail.size());
    }

    @Test
    public void testViolationOfHardConstraintFallsBackToNearestSeenNotFirstSeen() throws Exception {
        DependencyNode root = parseResource("range-backtracking.txt");

        assertSame(root, transform(root));

        List<DependencyNode> trail = find(root, "x");
        assertEquals(3, trail.size());
        assertEquals("2", trail.get(0).getArtifact().getVersion());
    }

    @Test
    public void testCyclicConflictIdGraph() throws Exception {
        DependencyNode root = parseResource("conflict-id-cycle.txt");

        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
        assertEquals("a", root.getChildren().get(0).getArtifact().getArtifactId());
        assertEquals("b", root.getChildren().get(1).getArtifact().getArtifactId());
        assertTrue(root.getChildren().get(0).getChildren().isEmpty());
        assertTrue(root.getChildren().get(1).getChildren().isEmpty());
    }

    @Test
    public void testUnsolvableRangeConflictBetweenHardConstraints() {
        assertThrows(UnsolvableVersionConflictException.class, () -> {
            DependencyNode root = parseResource("unsolvable.txt");
            transform(root);
        });
    }

    @Test
    public void testUnsolvableRangeConflictWithUnrelatedCycle() throws Exception {
        assertThrows(UnsolvableVersionConflictException.class, () -> {
            DependencyNode root = parseResource("unsolvable-with-cycle.txt");
            assertSame(root, transform(root));
        });
    }

    @Test
    public void testSolvableConflictBetweenHardConstraints() throws Exception {
        DependencyNode root = parseResource("ranges.txt");

        assertSame(root, transform(root));
    }

    @Test
    public void testConflictGroupCompletelyDroppedFromResolvedTree() throws Exception {
        DependencyNode root = parseResource("dead-conflict-group.txt");

        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
        assertEquals("a", root.getChildren().get(0).getArtifact().getArtifactId());
        assertEquals("b", root.getChildren().get(1).getArtifact().getArtifactId());
        assertTrue(root.getChildren().get(0).getChildren().isEmpty());
        assertTrue(root.getChildren().get(1).getChildren().isEmpty());
    }

    @Test
    public void testNearestSoftVersionPrunedByFartherRange() throws Exception {
        DependencyNode root = parseResource("soft-vs-range.txt");

        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
        assertEquals("a", root.getChildren().get(0).getArtifact().getArtifactId());
        assertEquals(0, root.getChildren().get(0).getChildren().size());
        assertEquals("b", root.getChildren().get(1).getArtifact().getArtifactId());
        assertEquals(1, root.getChildren().get(1).getChildren().size());
    }

    @Test
    public void testCyclicGraph() throws Exception {
        DependencyNode root = parseResource("cycle.txt");

        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
        assertEquals(1, root.getChildren().get(0).getChildren().size());
        assertEquals(
                0, root.getChildren().get(0).getChildren().get(0).getChildren().size());
        assertEquals(0, root.getChildren().get(1).getChildren().size());
    }

    @Test
    public void testLoop() throws Exception {
        DependencyNode root = parseResource("loop.txt");

        assertSame(root, transform(root));

        assertEquals(0, root.getChildren().size());
    }

    @Test
    public void testOverlappingCycles() throws Exception {
        DependencyNode root = parseResource("overlapping-cycles.txt");

        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
    }

    @Test
    public void testScopeDerivationAndConflictResolutionCantHappenForAllNodesBeforeVersionSelection() throws Exception {
        DependencyNode root = parseResource("scope-vs-version.txt");

        assertSame(root, transform(root));

        DependencyNode[] nodes = find(root, "y").toArray(new DependencyNode[0]);
        assertEquals(3, nodes.length);
        assertEquals("test", nodes[1].getDependency().getScope());
        assertEquals("test", nodes[0].getDependency().getScope());
    }

    @Test
    public void testVerboseMode() throws Exception {
        DependencyNode root = parseResource("verbose.txt");

        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.TRUE);
        assertSame(root, transform(root));

        assertEquals(2, root.getChildren().size());
        assertEquals(1, root.getChildren().get(0).getChildren().size());
        DependencyNode winner = root.getChildren().get(0).getChildren().get(0);
        assertEquals("test", winner.getDependency().getScope());
        assertEquals("compile", winner.getData().get(ConflictResolver.NODE_DATA_ORIGINAL_SCOPE));
        assertEquals(false, winner.getData().get(ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY));
        assertEquals(1, root.getChildren().get(1).getChildren().size());
        DependencyNode loser = root.getChildren().get(1).getChildren().get(0);
        assertEquals("test", loser.getDependency().getScope());
        assertEquals(0, loser.getChildren().size());
        assertSame(winner, loser.getData().get(ConflictResolver.NODE_DATA_WINNER));
        assertEquals("compile", loser.getData().get(ConflictResolver.NODE_DATA_ORIGINAL_SCOPE));
        assertEquals(false, loser.getData().get(ConflictResolver.NODE_DATA_ORIGINAL_OPTIONALITY));
    }
}
