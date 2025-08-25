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

import java.util.stream.Stream;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public final class SimpleOptionalitySelectorTest extends AbstractConflictResolverTest {
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
        return new DependencyGraphParser("transformer/optionality-selector/");
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void testDeriveOptionality(ConflictResolver conflictResolver) throws Exception {
        DependencyNode root = parseResource("derive.txt");
        assertSame(root, transform(conflictResolver, root));

        assertEquals(2, root.getChildren().size());
        assertTrue(root.getChildren().get(0).getDependency().isOptional());
        assertTrue(
                root.getChildren().get(0).getChildren().get(0).getDependency().isOptional());
        assertFalse(root.getChildren().get(1).getDependency().isOptional());
        assertFalse(
                root.getChildren().get(1).getChildren().get(0).getDependency().isOptional());
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void testResolveOptionalityConflict_NonOptionalWins(ConflictResolver conflictResolver) throws Exception {
        DependencyNode root = parseResource("conflict.txt");
        assertSame(root, transform(conflictResolver, root));

        assertEquals(2, root.getChildren().size());
        assertTrue(root.getChildren().get(0).getDependency().isOptional());
        assertFalse(
                root.getChildren().get(0).getChildren().get(0).getDependency().isOptional());
    }

    @ParameterizedTest
    @MethodSource("conflictResolverSource")
    void testResolveOptionalityConflict_DirectDeclarationWins(ConflictResolver conflictResolver) throws Exception {
        DependencyNode root = parseResource("conflict-direct-dep.txt");
        assertSame(root, transform(conflictResolver, root));

        assertEquals(2, root.getChildren().size());
        assertTrue(root.getChildren().get(1).getDependency().isOptional());
    }
}
