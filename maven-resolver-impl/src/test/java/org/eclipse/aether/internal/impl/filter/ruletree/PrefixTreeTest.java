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
package org.eclipse.aether.internal.impl.filter.ruletree;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link PrefixTree}.
 */
public class PrefixTreeTest {
    @Test
    void smoke() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("# comment", "", "/org/apache/maven"));
        prefixTree.dump("");

        assertFalse(prefixTree.acceptedPath("/com/google"));
        assertFalse(prefixTree.acceptedPath("/com/google/maven/aaa"));

        assertFalse(prefixTree.acceptedPath("/org/eclipse/maven"));
        assertFalse(prefixTree.acceptedPath("/org/eclipse/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven"));
        assertTrue(prefixTree.acceptedPath("/org/apache/maven/aaa"));

        assertFalse(prefixTree.acceptedPath("/org/apache/baz"));
        assertFalse(prefixTree.acceptedPath("/org/apache/baz/aaa"));
    }

    @Test
    void overlappingShallow() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("# comment", "/org", "/org/apache"));
        prefixTree.dump("");

        assertFalse(prefixTree.acceptedPath("/com/google"));
        assertFalse(prefixTree.acceptedPath("/com/google/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/eclipse/maven"));
        assertTrue(prefixTree.acceptedPath("/org/eclipse/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven"));
        assertTrue(prefixTree.acceptedPath("/org/apache/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/apache/baz"));
        assertTrue(prefixTree.acceptedPath("/org/apache/baz/aaa"));
    }

    @Test
    void overlappingDeep() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("# comment", "", "/org/apache", "/org/apache/maven"));
        prefixTree.dump("");

        assertFalse(prefixTree.acceptedPath("/com/google"));
        assertFalse(prefixTree.acceptedPath("/com/google/maven/aaa"));

        assertFalse(prefixTree.acceptedPath("/org/eclipse/maven"));
        assertFalse(prefixTree.acceptedPath("/org/eclipse/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven"));
        assertTrue(prefixTree.acceptedPath("/org/apache/maven/aaa"));

        assertTrue(prefixTree.acceptedPath("/org/apache/baz"));
        assertTrue(prefixTree.acceptedPath("/org/apache/baz/aaa"));
    }

    @Test
    void overlappingDeepFirst() {
        // order must not matter: shorter prefix wins regardless of load order
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("/org/apache/maven", "/org/apache"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven/aaa"));
        assertTrue(prefixTree.acceptedPath("/org/apache/baz"));
        assertFalse(prefixTree.acceptedPath("/org/eclipse"));
    }

    @Test
    void loadCountsOnlyPrefixLines() {
        PrefixTree prefixTree = new PrefixTree("root");
        assertEquals(
                3,
                prefixTree.loadNodes(Stream.of(
                        "## repository-prefixes/2.0",
                        "#",
                        "",
                        "   ",
                        "\t",
                        "/archetype-catalog.xml",
                        "/org/apache",
                        "/io")));
    }

    @Test
    void singleSegmentAndRootFile() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("/archetype-catalog.xml", "/antlr", "/io/netty"));

        assertTrue(prefixTree.acceptedPath("/archetype-catalog.xml"));
        assertFalse(prefixTree.acceptedPath("/archetype-other.xml"));
        assertTrue(prefixTree.acceptedPath("/antlr/antlr/2.7.7/antlr-2.7.7.pom"));
        assertFalse(prefixTree.acceptedPath("/io")); // only a parent of a prefix
        assertTrue(prefixTree.acceptedPath("/io/netty/netty-all/4.1/netty-all-4.1.jar"));
        assertFalse(prefixTree.acceptedPath("/io/grpc/grpc-api"));
    }

    @Test
    void siblingsAndSharedParents() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of(
                "/org/apache/maven", "/org/apache/ant", "/org/eclipse", "/org/codehaus/plexus", "/com/google/guava"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven/maven-core"));
        assertTrue(prefixTree.acceptedPath("/org/apache/ant/ant"));
        assertTrue(prefixTree.acceptedPath("/org/eclipse/aether/x"));
        assertTrue(prefixTree.acceptedPath("/org/codehaus/plexus/plexus-utils"));
        assertTrue(prefixTree.acceptedPath("/com/google/guava/guava"));

        assertFalse(prefixTree.acceptedPath("/org/apache/commons"));
        assertFalse(prefixTree.acceptedPath("/org/codehaus/mojo"));
        assertFalse(prefixTree.acceptedPath("/com/google/inject"));
        assertFalse(prefixTree.acceptedPath("/net/sf"));
    }

    @Test
    void segmentsMatchWholeNotByCharacterPrefix() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("/org/apache/maven"));

        assertFalse(prefixTree.acceptedPath("/org/apache/maven-extra/foo"));
        assertFalse(prefixTree.acceptedPath("/org/apache/mave"));
        assertFalse(prefixTree.acceptedPath("/org/apache")); // shorter than prefix
        assertFalse(prefixTree.acceptedPath("/org"));
    }

    @Test
    void slashNormalization() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("org/apache/maven/", "//org//eclipse//"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven"));
        assertTrue(prefixTree.acceptedPath("org/apache/maven/x"));
        assertTrue(prefixTree.acceptedPath("/org//eclipse/x/"));
    }

    @Test
    void emptyTreeAndDegeneratePaths() {
        PrefixTree prefixTree = new PrefixTree("root");
        assertFalse(prefixTree.acceptedPath("/org/apache"));
        assertFalse(prefixTree.acceptedPath("/"));
        assertFalse(prefixTree.acceptedPath(""));

        prefixTree.loadNodes(Stream.of("/org/apache"));
        assertFalse(prefixTree.acceptedPath("/"));
        assertFalse(prefixTree.acceptedPath(""));
    }

    @Test
    void duplicatesAreHarmless() {
        PrefixTree prefixTree = new PrefixTree("root");
        prefixTree.loadNodes(Stream.of("/org/apache/maven", "/org/apache/maven", "/org/apache/maven/plugins"));

        assertTrue(prefixTree.acceptedPath("/org/apache/maven/plugins/x"));
        assertTrue(prefixTree.acceptedPath("/org/apache/maven/other"));
        assertFalse(prefixTree.acceptedPath("/org/apache/ant"));
    }
}
