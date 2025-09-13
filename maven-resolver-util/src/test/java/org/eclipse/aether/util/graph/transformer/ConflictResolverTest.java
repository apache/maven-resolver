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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.internal.test.util.TestVersionConstraint;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public final class ConflictResolverTest extends AbstractConflictResolverTest {
    private static Stream<Arguments> conflictResolverSource() {
        return Stream.of(
                Arguments.of(new ClassicConflictResolver(
                        new NearestVersionSelector(),
                        new JavaScopeSelector(),
                        new SimpleOptionalitySelector(),
                        new JavaScopeDeriver())),
                Arguments.of(new PathConflictResolver(
                        new NearestVersionSelector(),
                        new JavaScopeSelector(),
                        new SimpleOptionalitySelector(),
                        new JavaScopeDeriver())));
    }

    @Override
    protected DependencyGraphParser newParser() {
        // is unused!!!
        return new DependencyGraphParser("transformer/conflict-resolver/");
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void noTransformationRequired(ConflictResolver conflictResolver) throws RepositoryException {
        // Foo -> Bar
        DependencyNode fooNode = makeDependencyNode("group-id", "foo", "1.0");
        DependencyNode barNode = makeDependencyNode("group-id", "bar", "1.0");
        fooNode.setChildren(mutableList(barNode));

        DependencyNode transformedNode = transform(conflictResolver, fooNode);

        assertSame(fooNode, transformedNode);
        assertEquals(1, transformedNode.getChildren().size());
        assertSame(barNode, transformedNode.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionClash(ConflictResolver conflictResolver) throws RepositoryException {
        // Foo -> Bar -> Baz 2.0
        //  |---> Baz 1.0
        DependencyNode fooNode = makeDependencyNode("some-group", "foo", "1.0");
        DependencyNode barNode = makeDependencyNode("some-group", "bar", "1.0");
        DependencyNode baz1Node = makeDependencyNode("some-group", "baz", "1.0");
        DependencyNode baz2Node = makeDependencyNode("some-group", "baz", "2.0");
        fooNode.setChildren(mutableList(barNode, baz1Node));
        barNode.setChildren(mutableList(baz2Node));

        DependencyNode transformedNode = transform(conflictResolver, fooNode);

        assertSame(fooNode, transformedNode);
        assertEquals(2, fooNode.getChildren().size());
        assertSame(barNode, fooNode.getChildren().get(0));
        assertTrue(barNode.getChildren().isEmpty());
        assertSame(baz1Node, fooNode.getChildren().get(1));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionClashForkedStandardVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        // root -> impl1 -> api:1
        //  |----> impl2 -> api:2
        DependencyNode root = makeDependencyNode("some-group", "root", "1.0");
        DependencyNode impl1 = makeDependencyNode("some-group", "impl1", "1.0");
        DependencyNode impl2 = makeDependencyNode("some-group", "impl2", "1.0");
        DependencyNode api1 = makeDependencyNode("some-group", "api", "1.1");
        DependencyNode api2 = makeDependencyNode("some-group", "api", "1.0");

        root.setChildren(mutableList(impl1, impl2));
        impl1.setChildren(mutableList(api1));
        impl2.setChildren(mutableList(api2));

        DependencyNode transformedNode = versionRangeClash(conflictResolver, root, ConflictResolver.Verbosity.STANDARD);

        assertSame(root, transformedNode);
        assertEquals(2, root.getChildren().size());
        assertSame(impl1, root.getChildren().get(0));
        assertSame(impl2, root.getChildren().get(1));
        assertEquals(1, impl1.getChildren().size());
        assertSame(api1, impl1.getChildren().get(0));
        assertEquals(1, impl2.getChildren().size());
        assertConflictedButSameAsOriginal(api2, impl2.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashAscOrder(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c1, c2));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.NONE);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(0, b.getChildren().size());
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashAscOrderStandardVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c1, c2));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.STANDARD);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(1, b.getChildren().size());
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashAscOrderFullVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c1, c2));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.FULL);

        assertSame(a, ta);
        assertEquals(3, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertConflictedButSameAsOriginal(c1, a.getChildren().get(1));
        assertSame(c2, a.getChildren().get(2));
        assertEquals(2, b.getChildren().size());
        assertConflictedButSameAsOriginal(c1, b.getChildren().get(0));
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(1));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashDescOrder(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c2, c1));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.NONE);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(0, b.getChildren().size());
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashDescOrderStandardVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c2, c1));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.STANDARD);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(1, b.getChildren().size());
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashDescOrderFullVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c2, c1));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.FULL);

        assertSame(a, ta);
        assertEquals(3, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertConflictedButSameAsOriginal(c1, a.getChildren().get(2));
        assertEquals(2, b.getChildren().size());
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(0));
        assertConflictedButSameAsOriginal(c1, b.getChildren().get(1));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashMixedOrder(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.NONE);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(0, b.getChildren().size());
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashMixedOrderStandardVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.STANDARD);

        assertSame(a, ta);
        assertEquals(2, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertEquals(1, b.getChildren().size());
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashMixedOrderStandardVerboseLeavesOne(ConflictResolver conflictResolver)
            throws RepositoryException {
        // This is a bit different then others, is related to MRESOLVER-357 and makes sure that
        // ConflictResolver fulfils the promise of "leaving 1 loser"
        //
        //  A -> B -> C[1..2]
        //  |    \ -> D1
        //  |--> C[1..2]
        //  \--> D2
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        DependencyNode d1 = makeDependencyNode("some-group", "d", "1.0");
        DependencyNode d2 = makeDependencyNode("some-group", "d", "2.0");
        a.setChildren(mutableList(b, c2, c1, d2));
        b.setChildren(mutableList(c1, c2, d1));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.STANDARD);

        assertSame(a, ta);
        assertEquals(3, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertSame(d2, a.getChildren().get(2));
        assertEquals(2, b.getChildren().size());
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(0));
        assertConflictedButSameAsOriginal(d1, b.getChildren().get(1));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void versionRangeClashMixedOrderFullVerbose(ConflictResolver conflictResolver) throws RepositoryException {
        //  A -> B -> C[1..2]
        //  \--> C[1..2]
        DependencyNode a = makeDependencyNode("some-group", "a", "1.0");
        DependencyNode b = makeDependencyNode("some-group", "b", "1.0");
        DependencyNode c1 = makeDependencyNode("some-group", "c", "1.0");
        DependencyNode c2 = makeDependencyNode("some-group", "c", "2.0");
        a.setChildren(mutableList(b, c2, c1));
        b.setChildren(mutableList(c1, c2));

        DependencyNode ta = versionRangeClash(conflictResolver, a, ConflictResolver.Verbosity.FULL);

        assertSame(a, ta);
        assertEquals(3, a.getChildren().size());
        assertSame(b, a.getChildren().get(0));
        assertSame(c2, a.getChildren().get(1));
        assertConflictedButSameAsOriginal(c1, a.getChildren().get(2));
        assertEquals(2, b.getChildren().size());
        assertConflictedButSameAsOriginal(c1, b.getChildren().get(0));
        assertConflictedButSameAsOriginal(c2, b.getChildren().get(1));
    }

    /**
     * Conflict resolver in case of conflict replaces {@link DependencyNode} instances with copies to keep them
     * stateful on different levels of graph and records conflict data. This method assert that two nodes do represent
     * same dependency (same GAV, scope, optionality), but that original is not conflicted while current is.
     */
    private void assertConflictedButSameAsOriginal(DependencyNode original, DependencyNode current) {
        assertNotSame(original, current);
        assertEquals(
                original.getDependency().getArtifact(), current.getDependency().getArtifact());
        assertEquals(
                original.getDependency().getScope(), current.getDependency().getScope());
        assertEquals(
                original.getDependency().getOptional(), current.getDependency().getOptional());
        assertNull(original.getData().get(ConflictResolver.NODE_DATA_WINNER));
        assertNotNull(current.getData().get(ConflictResolver.NODE_DATA_WINNER));
    }

    private static final DependencyGraphDumper DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    /**
     * Performs a verbose conflict resolution on passed in root.
     */
    private DependencyNode versionRangeClash(
            ConflictResolver conflictResolver, DependencyNode root, ConflictResolver.Verbosity verbosity)
            throws RepositoryException {
        System.out.println();
        System.out.println("Input node:");
        root.accept(DUMPER_SOUT);

        setVerbosity(verbosity);
        DependencyNode transformedRoot = transform(conflictResolver, root);

        System.out.println();
        System.out.println("Transformed node:");
        transformedRoot.accept(DUMPER_SOUT);

        return transformedRoot;
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void derivedScopeChange(ConflictResolver conflictResolver) throws RepositoryException {
        // Foo -> Bar (test) -> Jaz
        //  |---> Baz -> Jaz
        DependencyNode fooNode = makeDependencyNode("some-group", "foo", "1.0");
        DependencyNode barNode = makeDependencyNode("some-group", "bar", "1.0", "test");
        DependencyNode bazNode = makeDependencyNode("some-group", "baz", "1.0");
        DependencyNode jazNode = makeDependencyNode("some-group", "jaz", "1.0");
        fooNode.setChildren(mutableList(barNode, bazNode));

        List<DependencyNode> jazList = mutableList(jazNode);
        barNode.setChildren(jazList);
        bazNode.setChildren(jazList);

        DependencyNode transformedNode = transform(conflictResolver, fooNode);

        assertSame(fooNode, transformedNode);
        assertEquals(2, fooNode.getChildren().size());
        assertSame(barNode, fooNode.getChildren().get(0));
        assertEquals(1, barNode.getChildren().size());
        assertSame(jazNode, barNode.getChildren().get(0));
        assertSame(bazNode, fooNode.getChildren().get(1));
        assertEquals(1, barNode.getChildren().size());
        assertSame(jazNode, barNode.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void derivedOptionalStatusChange(ConflictResolver conflictResolver) throws RepositoryException {
        // Foo -> Bar (optional) -> Jaz
        //  |---> Baz -> Jaz
        DependencyNode fooNode = makeDependencyNode("some-group", "foo", "1.0");
        DependencyNode barNode = makeDependencyNode("some-group", "bar", "1.0");
        barNode.setOptional(true);
        DependencyNode bazNode = makeDependencyNode("some-group", "baz", "1.0");
        DependencyNode jazNode = makeDependencyNode("some-group", "jaz", "1.0");
        fooNode.setChildren(mutableList(barNode, bazNode));

        List<DependencyNode> jazList = mutableList(jazNode);
        barNode.setChildren(jazList);
        bazNode.setChildren(jazList);

        DependencyNode transformedNode = transform(conflictResolver, fooNode);

        assertSame(fooNode, transformedNode);
        assertEquals(2, fooNode.getChildren().size());
        assertSame(barNode, fooNode.getChildren().get(0));
        assertEquals(1, barNode.getChildren().size());
        assertSame(jazNode, barNode.getChildren().get(0));
        assertSame(bazNode, fooNode.getChildren().get(1));
        assertEquals(1, barNode.getChildren().size());
        assertSame(jazNode, barNode.getChildren().get(0));
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void cyclesAreKilledFromWinners(ConflictResolver conflictResolver) throws RepositoryException {
        // Foo -> bar -> baz -> bar
        DependencyNode fooNode = makeDependencyNode("some-group", "foo", "1.0");
        DependencyNode barNode = makeDependencyNode("some-group", "bar", "1.0");
        DependencyNode bazNode = makeDependencyNode("some-group", "baz", "1.0");
        fooNode.setChildren(mutableList(barNode));
        barNode.setChildren(mutableList(bazNode));
        bazNode.setChildren(mutableList(barNode));

        DependencyNode transformedNode = transform(conflictResolver, fooNode);

        assertSame(fooNode, transformedNode);
        assertEquals(1, fooNode.getChildren().size());
        assertSame(barNode, fooNode.getChildren().get(0));
        assertEquals(1, barNode.getChildren().size());
        assertSame(bazNode, barNode.getChildren().get(0));
        assertEquals(0, bazNode.getChildren().size());
    }

    private static DependencyNode makeDependencyNode(String groupId, String artifactId, String version) {
        return makeDependencyNode(groupId, artifactId, version, "compile");
    }

    private static DependencyNode makeDependencyNode(String groupId, String artifactId, String version, String scope) {
        DefaultDependencyNode node = new DefaultDependencyNode(
                new Dependency(new DefaultArtifact(groupId + ':' + artifactId + ':' + version), scope));
        node.setVersion(new TestVersion(version));
        node.setVersionConstraint(new TestVersionConstraint(node.getVersion()));
        return node;
    }

    private static List<DependencyNode> mutableList(DependencyNode... nodes) {
        return new ArrayList<>(Arrays.asList(nodes));
    }
}
