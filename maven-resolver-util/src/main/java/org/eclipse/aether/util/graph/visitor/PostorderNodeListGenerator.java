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
 * Generates a sequence of dependency nodes from a dependency graph by traversing the graph in postorder. This visitor
 * visits each node exactly once regardless how many paths within the dependency graph lead to the node such that the
 * resulting node sequence is free of duplicates.
 * <p>
 * The newer classes {@link AbstractDependencyNodeConsumerVisitor} and {@link NodeListGenerator} offer
 * similar capabilities but are pluggable. Use of this class, while not deprecated, is discouraged. This class
 * is not used in Resolver and is kept only for backward compatibility reasons.
 *
 * @see PostorderDependencyNodeConsumerVisitor
 * @see NodeListGenerator
 *
 * @deprecated See {@link PostorderDependencyNodeConsumerVisitor} that is more versatile.
 */
@Deprecated
public final class PostorderNodeListGenerator extends AbstractDepthFirstNodeListGenerator {

    private final Stack<Boolean> visits;

    /**
     * Creates a new postorder list generator.
     */
    public PostorderNodeListGenerator() {
        visits = new Stack<>();
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        boolean visited = !setVisited(node);

        visits.push(visited);

        return !visited;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        Boolean visited = visits.pop();

        if (visited) {
            return true;
        }

        if (node.getDependency() != null) {
            nodes.add(node);
        }

        return true;
    }
}
