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
import java.util.stream.Stream;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.visitor.DependencyGraphDumper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 */
public final class ConfigurableVersionSelectorStrategiesTest extends AbstractConflictResolverTest {
    private static final ConfigurableVersionSelector.SelectionStrategy NEAREST =
            new ConfigurableVersionSelector.Nearest();
    private static final ConfigurableVersionSelector.SelectionStrategy HIGHEST =
            new ConfigurableVersionSelector.Highest();

    private static Stream<Arguments> conflictResolverSource() {
        return Stream.of(
                // path + nearest
                Arguments.of(
                        NEAREST,
                        new PathConflictResolver(
                                new ConfigurableVersionSelector(NEAREST),
                                new JavaScopeSelector(),
                                new SimpleOptionalitySelector(),
                                new JavaScopeDeriver())),
                // path + highest
                Arguments.of(
                        HIGHEST,
                        new PathConflictResolver(
                                new ConfigurableVersionSelector(HIGHEST),
                                new JavaScopeSelector(),
                                new SimpleOptionalitySelector(),
                                new JavaScopeDeriver())),
                // classic + nearest
                Arguments.of(
                        NEAREST,
                        new ClassicConflictResolver(
                                new ConfigurableVersionSelector(NEAREST),
                                new JavaScopeSelector(),
                                new SimpleOptionalitySelector(),
                                new JavaScopeDeriver())),
                // classic + highest
                Arguments.of(
                        HIGHEST,
                        new ClassicConflictResolver(
                                new ConfigurableVersionSelector(HIGHEST),
                                new JavaScopeSelector(),
                                new SimpleOptionalitySelector(),
                                new JavaScopeDeriver())));
    }

    @Override
    protected DependencyGraphParser newParser() {
        return new DependencyGraphParser("transformer/version-resolver-strategies/");
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void testStrategyDifference01(
            ConfigurableVersionSelector.SelectionStrategy strategy, ConflictResolver conflictResolver)
            throws Exception {
        System.out.println(conflictResolver.versionSelector);
        DependencyNode root = parseResource("nearest-highest-strategy-difference01.txt");
        root.accept(new DependencyGraphDumper(System.out::println));
        assertSame(root, transform(conflictResolver, root));
        root.accept(new DependencyGraphDumper(System.out::println));

        List<DependencyNode> path = find(root, "x");
        assertEquals(2, path.size()); // x is at level 1; prevails in both strategies as it is direct dependency
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void testStrategyDifference02(
            ConfigurableVersionSelector.SelectionStrategy strategy, ConflictResolver conflictResolver)
            throws Exception {
        System.out.println(conflictResolver.versionSelector);
        DependencyNode root = parseResource("nearest-highest-strategy-difference02.txt");
        root.accept(new DependencyGraphDumper(System.out::println));
        assertSame(root, transform(conflictResolver, root));
        root.accept(new DependencyGraphDumper(System.out::println));

        if (strategy == NEAREST) {
            List<DependencyNode> path = find(root, "x");
            assertEquals(3, path.size()); // x is at level 2; is closest out of all x:1
            assertEquals("1", path.get(0).getVersion().toString());
        } else if (strategy == HIGHEST) {
            List<DependencyNode> path = find(root, "x");
            assertEquals(6, path.size()); // path is at level 5; is highest out of all x:3
            assertEquals("3", path.get(0).getVersion().toString());
        } else {
            throw new IllegalArgumentException("what strategy is this?");
        }
    }
}
