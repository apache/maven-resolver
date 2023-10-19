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

public class PatternExclusionsDependencyFilterTest {

    @Test
    public void acceptTestCornerCases() {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId("testArtifact");
        DependencyNode node = builder.build();
        List<DependencyNode> parents = new LinkedList<>();

        // Empty String, Empty List
        assertTrue(dontAccept(node, ""));
        assertTrue(new PatternExclusionsDependencyFilter(new LinkedList<String>()).accept(node, parents));
        assertTrue(new PatternExclusionsDependencyFilter((String[]) null).accept(node, parents));
        assertTrue(new PatternExclusionsDependencyFilter((VersionScheme) null, "[1,10]").accept(node, parents));
    }

    @Test
    public void acceptTestMatches() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        // full match
        assertTrue(
                dontAccept(node, "com.example.test:testArtifact:jar:1.0.3"), "com.example.test:testArtifact:jar:1.0.3");

        // single wildcard
        assertTrue(dontAccept(node, "*:testArtifact:jar:1.0.3"), "*:testArtifact:jar:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:*:jar:1.0.3"), "com.example.test:*:jar:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:testArtifact:*:1.0.3"), "com.example.test:testArtifact:*:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:testArtifact:*:1.0.3"), "com.example.test:testArtifact:*:1.0.3");

        // implicit wildcard
        assertTrue(dontAccept(node, ":testArtifact:jar:1.0.3"), ":testArtifact:jar:1.0.3");
        assertTrue(dontAccept(node, "com.example.test::jar:1.0.3"), "com.example.test::jar:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:testArtifact::1.0.3"), "com.example.test:testArtifact::1.0.3");
        assertTrue(dontAccept(node, "com.example.test:testArtifact:jar:"), "com.example.test:testArtifact:jar:");

        // multi wildcards
        assertTrue(dontAccept(node, "*:*:jar:1.0.3"), "*:*:jar:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:*:*:1.0.3"), "com.example.test:*:*:1.0.3");
        assertTrue(dontAccept(node, "com.example.test:testArtifact:*:*"), "com.example.test:testArtifact:*:*");
        assertTrue(dontAccept(node, "*:testArtifact:jar:*"), "*:testArtifact:jar:*");
        assertTrue(dontAccept(node, "*:*:jar:*"), "*:*:jar:*");
        assertTrue(dontAccept(node, ":*:jar:"), ":*:jar:");

        // partial wildcards
        assertTrue(dontAccept(node, "*.example.test:testArtifact:jar:1.0.3"), "*.example.test:testArtifact:jar:1.0.3");
        assertTrue(
                dontAccept(node, "com.example.test:testArtifact:*ar:1.0.*"), "com.example.test:testArtifact:*ar:1.0.*");
        assertTrue(
                dontAccept(node, "com.example.test:testArtifact:jar:1.0.*"), "com.example.test:testArtifact:jar:1.0.*");
        assertTrue(dontAccept(node, "*.example.*:testArtifact:jar:1.0.3"), "*.example.*:testArtifact:jar:1.0.3");

        // wildcard as empty string
        assertTrue(
                dontAccept(node, "com.example.test*:testArtifact:jar:1.0.3"),
                "com.example.test*:testArtifact:jar:1.0.3");
    }

    @Test
    public void acceptTestLessToken() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertTrue(dontAccept(node, "com.example.test:testArtifact:jar"), "com.example.test:testArtifact:jar");
        assertTrue(dontAccept(node, "com.example.test:testArtifact"), "com.example.test:testArtifact");
        assertTrue(dontAccept(node, "com.example.test"), "com.example.test");

        assertFalse(dontAccept(node, "com.example.foo"), "com.example.foo");
    }

    @Test
    public void acceptTestMismatch() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertFalse(dontAccept(node, "OTHER.GROUP.ID:testArtifact:jar:1.0.3"), "OTHER.GROUP.ID:testArtifact:jar:1.0.3");
        assertFalse(
                dontAccept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"),
                "com.example.test:OTHER_ARTIFACT:jar:1.0.3");
        assertFalse(
                dontAccept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"),
                "com.example.test:OTHER_ARTIFACT:jar:1.0.3");
        assertFalse(
                dontAccept(node, "com.example.test:testArtifact:WAR:1.0.3"), "com.example.test:testArtifact:WAR:1.0.3");
        assertFalse(
                dontAccept(node, "com.example.test:testArtifact:jar:SNAPSHOT"),
                "com.example.test:testArtifact:jar:SNAPSHOT");

        assertFalse(dontAccept(node, "*:*:war:*"), "*:*:war:*");
        assertFalse(dontAccept(node, "OTHER.GROUP.ID"), "OTHER.GROUP.ID");
    }

    @Test
    public void acceptTestMoreToken() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");

        DependencyNode node = builder.build();
        assertFalse(
                dontAccept(node, "com.example.test:testArtifact:jar:1.0.3:foo"),
                "com.example.test:testArtifact:jar:1.0.3:foo");
    }

    @Test
    public void acceptTestRange() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        String prefix = "com.example.test:testArtifact:jar:";

        assertTrue(dontAcceptVersionRange(node, prefix + "[1.0.3,1.0.4)"), prefix + "[1.0.3,1.0.4)");
        assertTrue(dontAcceptVersionRange(node, prefix + "[1.0.3,)"), prefix + "[1.0.3,)");
        assertTrue(dontAcceptVersionRange(node, prefix + "[1.0.3,]"), prefix + "[1.0.3,]");
        assertTrue(dontAcceptVersionRange(node, prefix + "(,1.0.3]"), prefix + "(,1.0.3]");
        assertTrue(dontAcceptVersionRange(node, prefix + "[1.0,]"), prefix + "[1.0,]");
        assertTrue(dontAcceptVersionRange(node, prefix + "[1,4]"), prefix + "[1,4]");
        assertTrue(dontAcceptVersionRange(node, prefix + "(1,4)"), prefix + "(1,4)");

        assertTrue(dontAcceptVersionRange(node, prefix + "(1.0.2,1.0.3]", prefix + "(1.1,)"), prefix + "(1.0.2,1.0.3]");

        assertFalse(dontAcceptVersionRange(node, prefix + "(1.0.3,2.0]"), prefix + "(1.0.3,2.0]");
        assertFalse(dontAcceptVersionRange(node, prefix + "(1,1.0.2]"), prefix + "(1,1.0.2]");

        assertFalse(
                dontAcceptVersionRange(node, prefix + "(1.0.2,1.0.3)", prefix + "(1.0.3,)"), prefix + "(1.0.2,1.0.3)");
    }

    private boolean dontAccept(DependencyNode node, String expression) {
        return !new PatternExclusionsDependencyFilter(expression).accept(node, new LinkedList<>());
    }

    private boolean dontAcceptVersionRange(DependencyNode node, String... expression) {
        return !new PatternExclusionsDependencyFilter(new GenericVersionScheme(), expression)
                .accept(node, new LinkedList<>());
    }
}
