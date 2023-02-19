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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.junit.Test;

import static org.junit.Assert.*;

public class ScopeDependencyFilterTest extends AbstractDependencyFilterTest {

    @Test
    public void acceptTest() {

        NodeBuilder builder = new NodeBuilder();
        builder.scope("compile").artifactId("test");
        List<DependencyNode> parents = new LinkedList<>();

        // null or empty
        assertTrue(new ScopeDependencyFilter(null, null).accept(builder.build(), parents));
        assertTrue(new ScopeDependencyFilter(new LinkedList<String>(), new LinkedList<String>())
                .accept(builder.build(), parents));
        assertTrue(new ScopeDependencyFilter((String[]) null).accept(builder.build(), parents));

        // only excludes
        assertTrue(new ScopeDependencyFilter("test").accept(builder.build(), parents));
        assertFalse(new ScopeDependencyFilter("compile").accept(builder.build(), parents));
        assertFalse(new ScopeDependencyFilter("compile", "test").accept(builder.build(), parents));

        // Both
        String[] excludes1 = {"provided"};
        String[] includes1 = {"compile", "test"};
        assertTrue(new ScopeDependencyFilter(Arrays.asList(includes1), Arrays.asList(excludes1))
                .accept(builder.build(), parents));
        assertTrue(new ScopeDependencyFilter(Arrays.asList(includes1), null).accept(builder.build(), parents));

        // exclude wins
        String[] excludes2 = {"compile"};
        String[] includes2 = {"compile"};
        assertFalse(new ScopeDependencyFilter(Arrays.asList(includes2), Arrays.asList(excludes2))
                .accept(builder.build(), parents));
    }
}
