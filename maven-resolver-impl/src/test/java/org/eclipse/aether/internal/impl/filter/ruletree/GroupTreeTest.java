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
        assertTrue(groupTree.acceptedGroupId("org.apache.maven"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.foo"));
        assertTrue(groupTree.acceptedGroupId("org.apache.maven.foo.aaa"));

        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar"));
        assertFalse(groupTree.acceptedGroupId("org.apache.maven.bar.aaa"));

        assertTrue(groupTree.acceptedGroupId("org.apache.baz"));
        assertFalse(groupTree.acceptedGroupId("org.apache.baz.aaa"));
    }
}
