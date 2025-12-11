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

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link GroupTree}.
 */
public class GroupTreeTest {
    @Test
    void smoke() {
        GroupTree groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of(
                "# comment",
                "",
                "org.apache.maven",
                "!=org.apache.maven.foo",
                "!org.apache.maven.bar",
                "=org.apache.baz"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache.maven"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.foo.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar"));
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar.aaa"));

        assertTrue(groupTree.acceptedGroupId("org.apache.baz"));
        assertFalse(groupTree.acceptedGroupId("org.apache.baz.aaa"));

        assertFalse(groupTree.acceptedGroupId("not.in.list.but.uses.default"));
    }

    @Test
    void smokeWithPositiveDefault() {
        GroupTree groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of(
                "# comment",
                "",
                "org.apache.maven",
                "!=org.apache.maven.foo",
                "!org.apache.maven.bar",
                "=org.apache.baz",
                "*"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache.maven"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.foo.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar"));
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar.aaa"));

        assertTrue(groupTree.acceptedGroupId("org.apache.baz"));
        assertTrue(groupTree.acceptedGroupId("org.apache.baz.aaa"));

        assertTrue(groupTree.acceptedGroupId("not.in.list.but.uses.default"));
    }

    @Test
    void smokeWithPositiveDefaultExclusionsOnly() {
        GroupTree groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of("# comment", "*", "!org.apache.maven.foo", "!=org.apache.maven.bar"));
        groupTree.dump("");

        // no applicable rule; root=ALLOW
        assertTrue(groupTree.acceptedGroupId("org.apache.maven"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.aaa"));

        // exclusion rule present (this and below)
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo"));
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo.aaa"));

        // exclusion+stop rule present (only this)
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.bar.aaa"));

        // no applicable rule; root=ALLOW
        assertTrue(groupTree.acceptedGroupId("not.in.list.but.uses.default"));
    }

    @Test
    void smokeWithNegativeDefault() {
        GroupTree groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of(
                "# comment",
                "",
                "org.apache.maven",
                "!=org.apache.maven.foo",
                "!org.apache.maven.bar",
                "=org.apache.baz",
                "!*"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache.maven"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.foo.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar"));
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar.aaa"));

        assertTrue(groupTree.acceptedGroupId("org.apache.baz"));
        assertFalse(groupTree.acceptedGroupId("org.apache.baz.aaa"));

        assertFalse(groupTree.acceptedGroupId("not.in.list.but.uses.default"));
    }

    @Test
    void implicitAndExplicitDefaultIsSame() {
        // w/o asterisk: uses coded defaults
        GroupTree implicitTree = new GroupTree("root");
        implicitTree.loadNodes(Stream.of(
                "# comment",
                "",
                "org.apache.maven",
                "!=org.apache.maven.foo",
                "!org.apache.maven.bar",
                "=org.apache.baz"));
        implicitTree.dump("");

        HashMap<String, Boolean> implicitResults = new HashMap<>();
        // w/ asterisk: set to same value as default (should cause no change)
        GroupTree explicitTree = new GroupTree("root");
        explicitTree.loadNodes(Stream.of(
                "# comment",
                "",
                "org.apache.maven",
                "!=org.apache.maven.foo",
                "!org.apache.maven.bar",
                "=org.apache.baz",
                "!*"));
        HashMap<String, Boolean> explicitResults = new HashMap<>();

        for (String key : Arrays.asList(
                "org.apache.maven",
                "org.apache.maven.aaa",
                "org.apache.maven.foo",
                "org.apache.maven.foo.aaa",
                "org.apache.maven.bar",
                "org.apache.maven.bar.aaa",
                "org.apache.baz",
                "org.apache.baz.aaa",
                "not.in.list.but.uses.default")) {
            implicitResults.put(key, implicitTree.acceptedGroupId(key));
            explicitResults.put(key, explicitTree.acceptedGroupId(key));
        }

        assertEquals(implicitResults, explicitResults);
    }

    @Test
    void gh1703One() {
        GroupTree groupTree;

        // REPRODUCER as given
        groupTree = new GroupTree("root");
        // this is redundant, as 'org.apache' IMPLIES 'org.apache.maven.plugins'
        groupTree.loadNodes(Stream.of("# comment", "", "org.apache", "org.apache.maven.plugins"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache")); // this is given
        assertTrue(groupTree.acceptedGroupId("org.apache.maven")); // implied by first
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins")); // implied by first (line is redundant)
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins.foo")); // implied by first

        // INVERTED REPRODUCER as given
        groupTree = new GroupTree("root");
        // this is redundant, as 'org.apache' IMPLIES 'org.apache.maven.plugins'
        groupTree.loadNodes(Stream.of("# comment", "", "org.apache.maven.plugins", "org.apache"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache")); // this is given
        assertTrue(groupTree.acceptedGroupId("org.apache.maven")); // implied by first
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins")); // implied by first (line is redundant)
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins.foo")); // implied by first

        // FIXED
        groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of("# comment", "", "=org.apache", "org.apache.maven.plugins"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache")); // this is given (=)
        assertFalse(groupTree.acceptedGroupId("org.apache.maven")); // not allowed
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins")); // this is given (and below)
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins.foo")); // implied by above

        // MIXED
        groupTree = new GroupTree("root");
        groupTree.loadNodes(Stream.of("# comment", "", "org.apache", "!=org.apache.maven.plugins"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache")); // this is given
        assertTrue(groupTree.acceptedGroupId("org.apache.maven")); // implied by first
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.plugins")); // this is given (!=)
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.plugins.foo")); // implied by first
    }

    @Test
    void gh1711() {
        GroupTree groupTree;

        // "last wins"
        groupTree = new GroupTree("root");
        // this is redundant, as 'org.apache' IMPLIES 'org.apache.maven.plugins'
        groupTree.loadNodes(Stream.of("org.apache", "!org.apache"));
        groupTree.dump("");

        assertFalse(groupTree.acceptedGroupId("org.apache")); // last wins
        assertFalse(groupTree.acceptedGroupId("org.apache.maven")); // last wins

        // "last wins"
        groupTree = new GroupTree("root");
        // this is redundant, as 'org.apache' IMPLIES 'org.apache.maven.plugins'
        groupTree.loadNodes(Stream.of("org.apache", "!org.apache", "org.apache"));
        groupTree.dump("");

        assertTrue(groupTree.acceptedGroupId("org.apache")); // last wins
        assertTrue(groupTree.acceptedGroupId("org.apache.maven")); // last wins
    }
}
