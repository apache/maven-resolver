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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that records the back-edges (cycles) of a dependency graph while still allowing another
 * visitor to render the graph. It uses the same seen-node logic as {@link TreeDependencyVisitor}: a node is visited
 * at most once, so a cyclic graph can be traversed without recursing forever. Unlike {@link TreeDependencyVisitor},
 * it keeps the current path from the root, which lets it tell a genuine back-edge from a diamond: a node reached a
 * second time is only a cycle when it is still an ancestor of the node being entered.
 * <p>
 * A recorded cycle is the path from the repeated ancestor to the current node, followed by the repeated node again.
 * For example, {@code [a, b, c, a]} means the edge {@code c -> a} closes the cycle. The delegate is allowed to see
 * that repeated node (so it can render a marker), but its children are not traversed.
 *
 * @since 2.0.27
 */
public final class CycleRecordingDependencyVisitor implements DependencyVisitor {

    private final DependencyVisitor visitor;

    private final Map<DependencyNode, Object> visitedNodes;

    private final Stack<Frame> stack;

    private final List<List<DependencyNode>> cycles;

    /**
     * Creates a visitor that records cycles without delegating to another visitor.
     */
    public CycleRecordingDependencyVisitor() {
        this(null);
    }

    /**
     * Creates a visitor that records cycles while delegating the visited nodes to the specified visitor. The delegate
     * sees every node once, plus the repeated node of each back-edge.
     *
     * @param visitor the visitor to delegate to, may be {@code null} for no delegation
     */
    public CycleRecordingDependencyVisitor(DependencyVisitor visitor) {
        this.visitor = visitor;
        this.visitedNodes = new IdentityHashMap<>(512);
        this.stack = new Stack<>();
        this.cycles = new ArrayList<>();
    }

    /**
     * Gets the cycles recorded during the graph visit. Each cycle starts and ends with the same node; the last two
     * nodes form the back-edge.
     *
     * @return the recorded cycles, never {@code null}
     */
    public List<List<DependencyNode>> getCycles() {
        return Collections.unmodifiableList(cycles);
    }

    /**
     * Gets the back-edges recorded during the graph visit, as pairs of {@code (source, target)} nodes.
     *
     * @return the recorded back-edges, never {@code null}
     */
    public List<Map.Entry<DependencyNode, DependencyNode>> getBackEdges() {
        List<Map.Entry<DependencyNode, DependencyNode>> backEdges = new ArrayList<>(cycles.size());
        for (List<DependencyNode> cycle : cycles) {
            int last = cycle.size() - 1;
            backEdges.add(new AbstractMap.SimpleImmutableEntry<>(cycle.get(last - 1), cycle.get(last)));
        }
        return Collections.unmodifiableList(backEdges);
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        boolean visited = visitedNodes.put(node, Boolean.TRUE) != null;

        int ancestor = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).node == node) {
                ancestor = i;
                break;
            }
        }

        boolean delegated = false;
        boolean descend = !visited;
        if (visited) {
            if (ancestor >= 0) {
                List<DependencyNode> cycle = new ArrayList<>();
                for (int i = ancestor; i < stack.size(); i++) {
                    cycle.add(stack.get(i).node);
                }
                cycle.add(node);
                cycles.add(Collections.unmodifiableList(cycle));
                if (visitor != null) {
                    visitor.visitEnter(node);
                    delegated = true;
                }
            }
            // A node seen on a different path is a diamond, not a cycle: suppress it as TreeDependencyVisitor does.
        } else if (visitor != null) {
            delegated = true;
            descend = visitor.visitEnter(node);
        }

        stack.push(new Frame(node, delegated));
        return descend;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        Frame frame = stack.pop();
        if (frame.delegated) {
            return visitor.visitLeave(frame.node);
        }
        return true;
    }

    private static final class Frame {

        private final DependencyNode node;

        private final boolean delegated;

        private Frame(DependencyNode node, boolean delegated) {
            this.node = node;
            this.delegated = delegated;
        }
    }
}
