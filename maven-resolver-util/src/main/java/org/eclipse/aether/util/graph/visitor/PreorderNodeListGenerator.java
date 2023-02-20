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
package org.eclipse.aether.util.graph.visitor;

import org.eclipse.aether.graph.DependencyNode;

/**
 * Generates a sequence of dependency nodes from a dependeny graph by traversing the graph in preorder. This visitor
 * visits each node exactly once regardless how many paths within the dependency graph lead to the node such that the
 * resulting node sequence is free of duplicates.
 */
public final class PreorderNodeListGenerator extends AbstractDepthFirstNodeListGenerator {

    /**
     * Creates a new preorder list generator.
     */
    public PreorderNodeListGenerator() {}

    @Override
    public boolean visitEnter(DependencyNode node) {
        if (!setVisited(node)) {
            return false;
        }

        if (node.getDependency() != null) {
            nodes.add(node);
        }

        return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        return true;
    }
}
