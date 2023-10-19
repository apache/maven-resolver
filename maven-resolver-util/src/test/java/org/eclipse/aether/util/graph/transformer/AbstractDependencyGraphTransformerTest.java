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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public abstract class AbstractDependencyGraphTransformerTest {

    protected DependencyGraphTransformer transformer;

    protected DependencyGraphParser parser;

    protected DefaultRepositorySystemSession session;

    protected DependencyGraphTransformationContext context;

    protected abstract DependencyGraphTransformer newTransformer();

    protected abstract DependencyGraphParser newParser();

    protected DependencyNode transform(DependencyNode root) throws Exception {
        context = TestUtils.newTransformationContext(session);
        root = transformer.transformGraph(root, context);
        assertNotNull(root);
        return root;
    }

    protected DependencyNode parseResource(String resource, String... substitutions) throws Exception {
        parser.setSubstitutions(substitutions);
        return parser.parseResource(resource);
    }

    protected DependencyNode parseLiteral(String literal, String... substitutions) throws Exception {
        parser.setSubstitutions(substitutions);
        return parser.parseLiteral(literal);
    }

    protected List<DependencyNode> find(DependencyNode node, String id) {
        LinkedList<DependencyNode> trail = new LinkedList<>();
        find(trail, node, id);
        return trail;
    }

    private boolean find(LinkedList<DependencyNode> trail, DependencyNode node, String id) {
        trail.addFirst(node);

        if (isMatch(node, id)) {
            return true;
        }

        for (DependencyNode child : node.getChildren()) {
            if (find(trail, child, id)) {
                return true;
            }
        }

        trail.removeFirst();

        return false;
    }

    private boolean isMatch(DependencyNode node, String id) {
        if (node.getDependency() == null) {
            return false;
        }
        return id.equals(node.getDependency().getArtifact().getArtifactId());
    }

    @BeforeEach
    public void setUp() {
        transformer = newTransformer();
        parser = newParser();
        session = new DefaultRepositorySystemSession();
    }

    @AfterEach
    public void tearDown() {
        transformer = null;
        parser = null;
        session = null;
        context = null;
    }
}
