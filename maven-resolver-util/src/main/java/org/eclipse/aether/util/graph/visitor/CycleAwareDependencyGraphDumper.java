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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.aether.graph.DependencyNode;

/**
 * A {@link DependencyGraphDumper} that is safe to use on a graph containing cycles: a dependency graph in
 * {@code FULL} verbosity mode may contain nodes that (directly or transitively) depend on an ancestor of
 * themselves, which makes the plain {@link DependencyGraphDumper} recurse forever. Wrapping it in
 * {@link TreeDependencyVisitor} avoids the stack overflow but also hides every node that was already visited
 * anywhere else in the graph, so a node reached a second time through a different path (a perfectly normal
 * diamond dependency, not a cycle) is silently dropped from the output as well.
 * <p>
 * This dumper only checks the path from the root to the node currently being visited: a node is only treated as
 * a cycle when it is already one of its own ancestors. Diamonds are still printed in full, and a true cycle is
 * printed once, marked, with its subtree cut off instead of being recursed into.
 *
 * @since 2.0.25
 */
public class CycleAwareDependencyGraphDumper extends DependencyGraphDumper {

    /**
     * Creates instance with given consumer.
     *
     * @param consumer the string consumer, must not be {@code null}
     */
    public CycleAwareDependencyGraphDumper(Consumer<String> consumer) {
        super(consumer);
    }

    /**
     * Creates instance with given consumer and decorators.
     *
     * @param consumer the string consumer, must not be {@code null}
     * @param decorators the decorators to apply, must not be {@code null}
     */
    public CycleAwareDependencyGraphDumper(
            Consumer<String> consumer, Collection<Function<DependencyNode, String>> decorators) {
        super(consumer, decorators);
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        if (isOnPath(node)) {
            nodes.push(node);
            consumer.accept(formatLine(nodes) + " (cycle)");
            return false;
        }
        return super.visitEnter(node);
    }

    /**
     * Returns {@code true} if {@code node} is already one of its own ancestors on the path from the root to the
     * node currently being visited, i.e. descending into it would recurse forever.
     */
    private boolean isOnPath(DependencyNode node) {
        for (DependencyNode ancestor : nodes) {
            if (ancestor == node) {
                return true;
            }
        }
        return false;
    }
}
