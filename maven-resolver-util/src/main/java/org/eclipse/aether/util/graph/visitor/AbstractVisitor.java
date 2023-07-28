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

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for dependency tree traverses.
 *
 * @since TBD
 */
abstract class AbstractVisitor implements DependencyVisitor {
    protected final ResettableDependencyNodeConsumer nodeConsumer;

    private final Map<DependencyNode, Object> visitedNodes;

    protected AbstractVisitor(ResettableDependencyNodeConsumer nodeConsumer) {
        this.nodeConsumer = requireNonNull(nodeConsumer);
        this.visitedNodes = new IdentityHashMap<>(512);
    }

    /**
     * Marks the specified node as being visited and determines whether the node has been visited before.
     *
     * @param node The node being visited, must not be {@code null}.
     * @return {@code true} if the node has not been visited before, {@code false} if the node was already visited.
     */
    protected boolean setVisited(DependencyNode node) {
        return visitedNodes.put(node, Boolean.TRUE) == null;
    }

    @Override
    public abstract boolean visitEnter(DependencyNode node);

    @Override
    public abstract boolean visitLeave(DependencyNode node);
}
