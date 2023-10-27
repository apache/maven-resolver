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

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class JavaDependencyContextRefinerTest extends AbstractDependencyGraphTransformerTest {

    @Override
    protected DependencyGraphTransformer newTransformer() {
        return new JavaDependencyContextRefiner();
    }

    @Override
    protected DependencyGraphParser newParser() {
        return new DependencyGraphParser("transformer/context-refiner/");
    }

    @Test
    void testDoNotRefineOtherContext() throws Exception {
        DependencyNode node = parseLiteral("gid:aid:cls:ver");
        node.setRequestContext("otherContext");

        DependencyNode refinedNode = transform(node);
        assertEquals(node, refinedNode);
    }

    @Test
    void testRefineToCompile() throws Exception {
        String expected = "project/compile";

        DependencyNode node = parseLiteral("gid:aid:ver compile");
        node.setRequestContext("project");
        DependencyNode refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());

        node = parseLiteral("gid:aid:ver system");
        node.setRequestContext("project");
        refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());

        node = parseLiteral("gid:aid:ver provided");
        node.setRequestContext("project");
        refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());
    }

    @Test
    void testRefineToTest() throws Exception {
        String expected = "project/test";

        DependencyNode node = parseLiteral("gid:aid:ver test");
        node.setRequestContext("project");
        DependencyNode refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());
    }

    @Test
    void testRefineToRuntime() throws Exception {
        String expected = "project/runtime";

        DependencyNode node = parseLiteral("gid:aid:ver runtime");
        node.setRequestContext("project");
        DependencyNode refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());
    }

    @Test
    void testDoNotRefineUnknownScopes() throws Exception {
        String expected = "project";

        DependencyNode node = parseLiteral("gid:aid:ver unknownScope");
        node.setRequestContext("project");
        DependencyNode refinedNode = transform(node);
        assertEquals(expected, refinedNode.getRequestContext());
    }
}
