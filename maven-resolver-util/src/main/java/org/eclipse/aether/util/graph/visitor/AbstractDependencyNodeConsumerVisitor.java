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
import java.util.function.Consumer;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for dependency tree traverses that feed {@link Consumer<DependencyNode>}.
 * <p>
 * <strong>Implementations derived from this class cannot be embedded into {@link FilteringDependencyVisitor}</strong>,
 * this is why these classes accept {@link DependencyFilter} in constructor instead.
 *
 * @since 2.0.0
 */
abstract class AbstractDependencyNodeConsumerVisitor implements DependencyVisitor {
    private static final DependencyFilter ACCEPT_ALL = (d, p) -> true;

    private final Consumer<DependencyNode> nodeConsumer;

    private final DependencyFilter filter;

    private final Stack<DependencyNode> path;

    private final Map<DependencyNode, Object> visitedNodes;

    protected AbstractDependencyNodeConsumerVisitor(Consumer<DependencyNode> nodeConsumer, DependencyFilter filter) {
        this.nodeConsumer = requireNonNull(nodeConsumer);
        this.filter = filter == null ? ACCEPT_ALL : filter;
        this.path = new Stack<>();
        this.visitedNodes = new IdentityHashMap<>(512);
    }

    /**
     * Marks the specified node as being visited and determines whether the node has been visited before.
     *
     * @param node the node being visited, must not be {@code null}
     * @return {@code true} if the node has not been visited before, {@code false} if the node was already visited
     */
    protected boolean setVisited(DependencyNode node) {
        return visitedNodes.put(node, Boolean.TRUE) == null;
    }

    @Override
    public final boolean visitEnter(DependencyNode node) {
        path.push(node);
        return doVisitEnter(node);
    }

    protected abstract boolean doVisitEnter(DependencyNode node);

    @Override
    public final boolean visitLeave(DependencyNode node) {
        path.pop();
        return doVisitLeave(node);
    }

    protected abstract boolean doVisitLeave(DependencyNode node);

    protected boolean acceptNode(DependencyNode node) {
        return filter.accept(node, path.head());
    }

    protected void consumeNode(DependencyNode node) {
        nodeConsumer.accept(node);
    }
}
