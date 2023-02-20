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
import org.junit.Test;

import static org.junit.Assert.*;

public class PatternInclusionsDependencyFilterTest extends AbstractDependencyFilterTest {

    @Test
    public void acceptTestCornerCases() {
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
    public void acceptTestMatches() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        // full match
        assertTrue("com.example.test:testArtifact:jar:1.0.3", accept(node, "com.example.test:testArtifact:jar:1.0.3"));

        // single wildcard
        assertTrue("*:testArtifact:jar:1.0.3", accept(node, "*:testArtifact:jar:1.0.3"));
        assertTrue("com.example.test:*:jar:1.0.3", accept(node, "com.example.test:*:jar:1.0.3"));
        assertTrue("com.example.test:testArtifact:*:1.0.3", accept(node, "com.example.test:testArtifact:*:1.0.3"));
        assertTrue("com.example.test:testArtifact:*:1.0.3", accept(node, "com.example.test:testArtifact:*:1.0.3"));

        // implicit wildcard
        assertTrue(":testArtifact:jar:1.0.3", accept(node, ":testArtifact:jar:1.0.3"));
        assertTrue("com.example.test::jar:1.0.3", accept(node, "com.example.test::jar:1.0.3"));
        assertTrue("com.example.test:testArtifact::1.0.3", accept(node, "com.example.test:testArtifact::1.0.3"));
        assertTrue("com.example.test:testArtifact:jar:", accept(node, "com.example.test:testArtifact:jar:"));

        // multi wildcards
        assertTrue("*:*:jar:1.0.3", accept(node, "*:*:jar:1.0.3"));
        assertTrue("com.example.test:*:*:1.0.3", accept(node, "com.example.test:*:*:1.0.3"));
        assertTrue("com.example.test:testArtifact:*:*", accept(node, "com.example.test:testArtifact:*:*"));
        assertTrue("*:testArtifact:jar:*", accept(node, "*:testArtifact:jar:*"));
        assertTrue("*:*:jar:*", accept(node, "*:*:jar:*"));
        assertTrue(":*:jar:", accept(node, ":*:jar:"));

        // partial wildcards
        assertTrue("*.example.test:testArtifact:jar:1.0.3", accept(node, "*.example.test:testArtifact:jar:1.0.3"));
        assertTrue("com.example.test:testArtifact:*ar:1.0.*", accept(node, "com.example.test:testArtifact:*ar:1.0.*"));
        assertTrue("com.example.test:testArtifact:jar:1.0.*", accept(node, "com.example.test:testArtifact:jar:1.0.*"));
        assertTrue("*.example.*:testArtifact:jar:1.0.3", accept(node, "*.example.*:testArtifact:jar:1.0.3"));

        // wildcard as empty string
        assertTrue(
                "com.example.test*:testArtifact:jar:1.0.3", accept(node, "com.example.test*:testArtifact:jar:1.0.3"));
    }

    @Test
    public void acceptTestLessToken() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertTrue("com.example.test:testArtifact:jar", accept(node, "com.example.test:testArtifact:jar"));
        assertTrue("com.example.test:testArtifact", accept(node, "com.example.test:testArtifact"));
        assertTrue("com.example.test", accept(node, "com.example.test"));

        assertFalse("com.example.foo", accept(node, "com.example.foo"));
    }

    @Test
    public void acceptTestMissmatch() {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId("com.example.test")
                .artifactId("testArtifact")
                .ext("jar")
                .version("1.0.3");
        DependencyNode node = builder.build();

        assertFalse("OTHER.GROUP.ID:testArtifact:jar:1.0.3", accept(node, "OTHER.GROUP.ID:testArtifact:jar:1.0.3"));
        assertFalse(
                "com.example.test:OTHER_ARTIFACT:jar:1.0.3", accept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"));
        assertFalse(
                "com.example.test:OTHER_ARTIFACT:jar:1.0.3", accept(node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3"));
        assertFalse("com.example.test:testArtifact:WAR:1.0.3", accept(node, "com.example.test:testArtifact:WAR:1.0.3"));
        assertFalse(
                "com.example.test:testArtifact:jar:SNAPSHOT",
                accept(node, "com.example.test:testArtifact:jar:SNAPSHOT"));

        assertFalse("*:*:war:*", accept(node, "*:*:war:*"));
        assertFalse("OTHER.GROUP.ID", accept(node, "OTHER.GROUP.ID"));
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
                "com.example.test:testArtifact:jar:1.0.3:foo",
                accept(node, "com.example.test:testArtifact:jar:1.0.3:foo"));
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

        assertTrue(prefix + "[1.0.3,1.0.4)", acceptVersionRange(node, prefix + "[1.0.3,1.0.4)"));
        assertTrue(prefix + "[1.0.3,)", acceptVersionRange(node, prefix + "[1.0.3,)"));
        assertTrue(prefix + "[1.0.3,]", acceptVersionRange(node, prefix + "[1.0.3,]"));
        assertTrue(prefix + "(,1.0.3]", acceptVersionRange(node, prefix + "(,1.0.3]"));
        assertTrue(prefix + "[1.0,]", acceptVersionRange(node, prefix + "[1.0,]"));
        assertTrue(prefix + "[1,4]", acceptVersionRange(node, prefix + "[1,4]"));
        assertTrue(prefix + "(1,4)", acceptVersionRange(node, prefix + "(1,4)"));

        assertTrue(prefix + "(1.0.2,1.0.3]", acceptVersionRange(node, prefix + "(1.0.2,1.0.3]", prefix + "(1.1,)"));

        assertFalse(prefix + "(1.0.3,2.0]", acceptVersionRange(node, prefix + "(1.0.3,2.0]"));
        assertFalse(prefix + "(1,1.0.2]", acceptVersionRange(node, prefix + "(1,1.0.2]"));

        assertFalse(prefix + "(1.0.2,1.0.3)", acceptVersionRange(node, prefix + "(1.0.2,1.0.3)", prefix + "(1.0.3,)"));
    }

    public boolean accept(DependencyNode node, String expression) {
        return new PatternInclusionsDependencyFilter(expression).accept(node, new LinkedList<DependencyNode>());
    }

    public boolean acceptVersionRange(DependencyNode node, String... expression) {
        return new PatternInclusionsDependencyFilter(new GenericVersionScheme(), expression)
                .accept(node, new LinkedList<DependencyNode>());
    }
}
