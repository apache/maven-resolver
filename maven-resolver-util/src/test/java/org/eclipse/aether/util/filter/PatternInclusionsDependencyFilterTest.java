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
package org.eclipse.aether.util.filter;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.VersionScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PatternInclusionsDependencyFilterTest extends AbstractDependencyFilterTest {

    @Test
    void acceptTestCornerCases() {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId("testArtifact");
        DependencyNode node = builder.build();
        List<DependencyNode> parents = new LinkedList<>();

        // Empty String, Empty List
        assertTrue(accept(node, ""));
        assertFalse(new PatternInclusionsDependencyFilter(new LinkedList<String>()).accept(node, parents));
        assertFalse(new PatternInclusionsDependencyFilter((String[]) null).accept(node, parents));
        assertFalse(new PatternInclusionsDependencyFilter((VersionScheme) null, "[1,10]").accept(node, parents));
    }

    @Test
    void acceptTestMatches() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        // full match
        assertTrue(accept(node, "com.example.test:testArtifact:jar:1.0.3"), "com.example.test:testArtifact:jar:1.0.3");

        // single wildcard
        assertTrue(accept(node, "*:testArtifact:jar:1.0.3"), "*:testArtifact:jar:1.0.3");
        assertTrue(accept(node, "com.example.test:*:jar:1.0.3"), "com.example.test:*:jar:1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact:*:1.0.3"), "com.example.test:testArtifact:*:1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact:*:1.0.3"), "com.example.test:testArtifact:*:1.0.3");

        // implicit wildcard
        assertTrue(accept(node, ":testArtifact:jar:1.0.3"), ":testArtifact:jar:1.0.3");
        assertTrue(accept(node, "com.example.test::jar:1.0.3"), "com.example.test::jar:1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact::1.0.3"), "com.example.test:testArtifact::1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact:jar:"), "com.example.test:testArtifact:jar:");

        // multi wildcards
        assertTrue(accept(node, "*:*:jar:1.0.3"), "*:*:jar:1.0.3");
        assertTrue(accept(node, "com.example.test:*:*:1.0.3"), "com.example.test:*:*:1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact:*:*"), "com.example.test:testArtifact:*:*");
        assertTrue(accept(node, "*:testArtifact:jar:*"), "*:testArtifact:jar:*");
        assertTrue(accept(node, "*:*:jar:*"), "*:*:jar:*");
        assertTrue(accept(node, ":*:jar:"), ":*:jar:");

        // partial wildcards
        assertTrue(accept(node, "*.example.test:testArtifact:jar:1.0.3"), "*.example.test:testArtifact:jar:1.0.3");
        assertTrue(accept(node, "com.example.test:testArtifact:*ar:1.0.*"), "com.example.test:testArtifact:*ar:1.0.*");
        assertTrue(accept(node, "com.example.test:testArtifact:jar:1.0.*"), "com.example.test:testArtifact:jar:1.0.*");
        assertTrue(accept(node, "*.example.*:testArtifact:jar:1.0.3"), "*.example.*:testArtifact:jar:1.0.3");

        // wildcard as empty string
        assertTrue(
                accept(node, "com.example.test*:testArtifact:jar:1.0.3"), "com.example.test*:testArtifact:jar:1.0.3");
    }

    @Test
    void acceptTestLessToken() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertTrue(accept(node, "com.example.test:testArtifact:jar"), "com.example.test:testArtifact:jar");
        assertTrue(accept(node, "com.example.test:testArtifact"), "com.example.test:testArtifact");
        assertTrue(accept(node, "com.example.test"), "com.example.test");

        assertFalse(accept(node, "com.example.foo"), "com.example.foo");
    }

    @Test
    void acceptTestMissmatch() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertFalse(accept(node, "OTHER.GROUP.ID:testArtifact:jar:1.0.3"), "OTHER.GROUP.ID:testArtifact:jar:1.0.3");
        assertFalse(
                accept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"), "com.example.test:OTHER_ARTIFACT:jar:1.0.3");
        assertFalse(
                accept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"), "com.example.test:OTHER_ARTIFACT:jar:1.0.3");
        assertFalse(accept(node, "com.example.test:testArtifact:WAR:1.0.3"), "com.example.test:testArtifact:WAR:1.0.3");
        assertFalse(
                accept(node, "com.example.test:testArtifact:jar:SNAPSHOT"),
                "com.example.test:testArtifact:jar:SNAPSHOT");

        assertFalse(accept(node, "*:*:war:*"), "*:*:war:*");
        assertFalse(accept(node, "OTHER.GROUP.ID"), "OTHER.GROUP.ID");
    }

    @Test
    void acceptTestMoreToken() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");

        DependencyNode node = builder.build();
        assertFalse(
                accept(node, "com.example.test:testArtifact:jar:1.0.3:foo"),
                "com.example.test:testArtifact:jar:1.0.3:foo");
    }

    @Test
    void acceptTestRange() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        String prefix = "com.example.test:testArtifact:jar:";

        assertTrue(acceptVersionRange(node, prefix + "[1.0.3,1.0.4)"), prefix + "[1.0.3,1.0.4)");
        assertTrue(acceptVersionRange(node, prefix + "[1.0.3,)"), prefix + "[1.0.3,)");
        assertTrue(acceptVersionRange(node, prefix + "[1.0.3,]"), prefix + "[1.0.3,]");
        assertTrue(acceptVersionRange(node, prefix + "(,1.0.3]"), prefix + "(,1.0.3]");
        assertTrue(acceptVersionRange(node, prefix + "[1.0,]"), prefix + "[1.0,]");
        assertTrue(acceptVersionRange(node, prefix + "[1,4]"), prefix + "[1,4]");
        assertTrue(acceptVersionRange(node, prefix + "(1,4)"), prefix + "(1,4)");

        assertTrue(acceptVersionRange(node, prefix + "(1.0.2,1.0.3]", prefix + "(1.1,)"), prefix + "(1.0.2,1.0.3]");

        assertFalse(acceptVersionRange(node, prefix + "(1.0.3,2.0]"), prefix + "(1.0.3,2.0]");
        assertFalse(acceptVersionRange(node, prefix + "(1,1.0.2]"), prefix + "(1,1.0.2]");

        assertFalse(acceptVersionRange(node, prefix + "(1.0.2,1.0.3)", prefix + "(1.0.3,)"), prefix + "(1.0.2,1.0.3)");
    }

    public boolean accept(DependencyNode node, String expression) {
        return new PatternInclusionsDependencyFilter(expression).accept(node, new LinkedList<>());
    }

    public boolean acceptVersionRange(DependencyNode node, String... expression) {
        return new PatternInclusionsDependencyFilter(new GenericVersionScheme(), expression)
                .accept(node, new LinkedList<>());
    }
}
