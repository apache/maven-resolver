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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.internal.test.util.TestVersionConstraint;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
public class ConflictResolverJMHBenchmark {
    private static final RepositorySystemSession session = TestUtils.newSession();

    private ConflictResolver classic;
    private ConflictResolver path;

    @Setup
    public void setup() {
        classic = new ClassicConflictResolver(
                new ConfigurableVersionSelector(),
                new JavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver());
        path = new PathConflictResolver(
                new ConfigurableVersionSelector(),
                new JavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver());
    }

    public static void main(String... args) throws RunnerException {
        new Runner(new OptionsBuilder()
                        .include(ConflictResolverJMHBenchmark.class.getSimpleName())
                        .build())
                .run();
    }

    @Benchmark
    public void uniqueSnake_20_path() throws RepositoryException {
        uniqueSnake(path, 20);
    }

    @Benchmark
    public void uniqueSnake_20_classic() throws RepositoryException {
        uniqueSnake(classic, 20);
    }

    @Benchmark
    public void uniqueSnake_40_path() throws RepositoryException {
        uniqueSnake(path, 40);
    }

    @Benchmark
    public void uniqueSnake_40_classic() throws RepositoryException {
        uniqueSnake(classic, 40);
    }

    @Benchmark
    public void uniqueSnakeWithRootCycle_20_path() throws RepositoryException {
        uniqueSnakeWithRootCycle(path, 20);
    }

    @Benchmark
    public void uniqueSnakeWithRootCycle_20_classic() throws RepositoryException {
        uniqueSnakeWithRootCycle(classic, 20);
    }

    @Benchmark
    public void uniqueSnakeWithRootCycle_40_path() throws RepositoryException {
        uniqueSnakeWithRootCycle(path, 40);
    }

    @Benchmark
    public void uniqueSnakeWithRootCycle_40_classic() throws RepositoryException {
        uniqueSnakeWithRootCycle(classic, 40);
    }

    @Benchmark
    public void symmetricBinaryTreeUnique_5_path() throws RepositoryException {
        symmetricBinaryTree(path, 5, Integer.MAX_VALUE);
    }

    @Benchmark
    public void symmetricBinaryTreeUnique_5_classic() throws RepositoryException {
        symmetricBinaryTree(classic, 5, Integer.MAX_VALUE);
    }

    @Benchmark
    public void symmetricBinaryTreeMod50_5_path() throws RepositoryException {
        symmetricBinaryTree(path, 5, 50);
    }

    @Benchmark
    public void symmetricBinaryTreeMod50_5_classic() throws RepositoryException {
        symmetricBinaryTree(classic, 5, 50);
    }

    /**
     * A "snake", plain chain of unique dependencies of given length.
     */
    private static void uniqueSnake(ConflictResolver conflictResolver, int length) throws RepositoryException {
        DependencyNode root = makeDependencyNode("group-id", "root", "1.0");
        DependencyNode last = root;
        for (int i = 0; i < length; i++) {
            DependencyNode dep = makeDependencyNode("group-id", "dep-" + i, "1.0");
            last.setChildren(mutableList(dep));
            last = dep;
        }

        DependencyNode transformedNode = transform(conflictResolver, root);

        assertSame(root, transformedNode);
        assertEquals(1, transformedNode.getChildren().size());
    }

    /**
     * A "snake", plain chain of unique dependencies of given length, where last dep points back to root forming a
     * cycle.
     */
    private static void uniqueSnakeWithRootCycle(ConflictResolver conflictResolver, int length)
            throws RepositoryException {
        DependencyNode root = makeDependencyNode("group-id", "root", "1.0");
        DependencyNode last = root;
        for (int i = 0; i < length; i++) {
            DependencyNode dep = makeDependencyNode("group-id", "dep-" + i, "1.0");
            last.setChildren(mutableList(dep));
            last = dep;
        }
        last.setChildren(mutableList(root));

        DependencyNode transformedNode = transform(conflictResolver, root);

        assertSame(root, transformedNode);
        assertEquals(1, transformedNode.getChildren().size());
    }

    /**
     * A symmetric binary tree with given depth. Provided modulo is to create conflicts, if larger that total tree nodes,
     * tree will be "unique" (no conflicts).
     */
    private static void symmetricBinaryTree(ConflictResolver conflictResolver, int depth, int modulo)
            throws RepositoryException {
        DependencyNode root = makeDependencyNode("group-id", "root", "1.0");
        int level = 2;
        int idCounter = 1;
        ArrayDeque<DependencyNode> stack = new ArrayDeque<>();
        stack.push(root);
        for (int i = 0; i < depth; i++) {
            ArrayList<DependencyNode> children = new ArrayList<>();
            while (!stack.isEmpty()) {
                DependencyNode node = stack.pop();
                DependencyNode left = makeDependencyNode("group-id", "d" + idCounter++ % modulo, "1.0");
                DependencyNode right = makeDependencyNode("group-id", "d" + idCounter++ % modulo, "1.0");
                node.setChildren(mutableList(left, right));
                children.add(left);
                children.add(right);
            }
            stack.addAll(children);
        }

        DependencyNode transformedNode = transform(conflictResolver, root);

        assertSame(root, transformedNode);
    }

    private static final DependencyGraphDumper DUMPER_SOUT = new DependencyGraphDumper(System.out::println);

    private static DependencyNode transform(ConflictResolver conflictResolver, DependencyNode root)
            throws RepositoryException {
        DependencyGraphTransformationContext context = TestUtils.newTransformationContext(session);
        root = conflictResolver.transformGraph(root, context);
        assertNotNull(root);
        return root;
    }

    private static DependencyNode makeDependencyNode(String groupId, String artifactId, String version) {
        return makeDependencyNode(groupId, artifactId, version, "compile");
    }

    private static DependencyNode makeDependencyNode(String groupId, String artifactId, String version, String scope) {
        return makeDependencyNode(groupId, artifactId, version, null, "compile");
    }

    private static DependencyNode makeDependencyNode(
            String groupId, String artifactId, String version, String classifier, String scope) {
        DefaultDependencyNode node = (classifier != null && !classifier.isEmpty())
                ? new DefaultDependencyNode(new Dependency(
                        new DefaultArtifact(groupId + ':' + artifactId + ":jar:" + classifier + ":" + version), scope))
                : new DefaultDependencyNode(
                        new Dependency(new DefaultArtifact(groupId + ':' + artifactId + ':' + version), scope));
        node.setVersion(new TestVersion(version));
        node.setVersionConstraint(new TestVersionConstraint(node.getVersion()));
        return node;
    }

    private static List<DependencyNode> mutableList(DependencyNode... nodes) {
        return new ArrayList<>(Arrays.asList(nodes));
    }
}
