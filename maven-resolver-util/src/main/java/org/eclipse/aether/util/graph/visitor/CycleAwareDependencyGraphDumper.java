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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import static java.util.Objects.requireNonNull;

/**
 * A dependency visitor that visualizes cycles in dependency graphs while preventing StackOverflow errors.
 * This visitor wraps a {@link DependencyGraphDumper} and adds cycle detection and visualization capabilities.
 * <p>
 * When a cycle is detected (a node with the same versionless artifact ID appears again in the current path),
 * it is displayed with a reference notation like {@code ^N} where N is the index of the node in the path that
 * it cycles back to. The visitor then stops traversing children of the cycle node to prevent infinite recursion.
 * </p>
 * <p>
 * This visitor is particularly useful for visualizing dependency graphs in FULL verbosity mode where cycles
 * are preserved in the graph structure.
 * </p>
 *
 * @since 2.0.0
 */
public class CycleAwareDependencyGraphDumper implements DependencyVisitor {

    private final Consumer<String> consumer;
    private final DependencyGraphDumper dumper;
    private final Deque<DependencyNode> currentPath;
    private final Stack<Boolean> isCycleStack;

    /**
     * Creates a new cycle-aware dependency graph dumper with the specified consumer.
     *
     * @param consumer the string consumer, must not be {@code null}
     */
    public CycleAwareDependencyGraphDumper(Consumer<String> consumer) {
        this.consumer = requireNonNull(consumer, "consumer cannot be null");
        this.dumper = new DependencyGraphDumper(consumer);
        this.currentPath = new ArrayDeque<>();
        this.isCycleStack = new Stack<>();
    }

    /**
     * Creates a new cycle-aware dependency graph dumper with the specified consumer and decorators.
     *
     * @param consumer the string consumer, must not be {@code null}
     * @param decorators the decorators to apply, must not be {@code null}
     */
    public CycleAwareDependencyGraphDumper(
            Consumer<String> consumer, Collection<Function<DependencyNode, String>> decorators) {
        this.consumer = requireNonNull(consumer, "consumer cannot be null");
        this.dumper = new DependencyGraphDumper(consumer, decorators);
        this.currentPath = new ArrayDeque<>();
        this.isCycleStack = new Stack<>();
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        requireNonNull(node, "node cannot be null");

        // Check for cycle BEFORE adding current node to path
        int cycleIndex = findCycleInPath(node);
        boolean isCycle = cycleIndex >= 0;

        // Add node to path after checking (for formatting purposes)
        currentPath.push(node);
        isCycleStack.push(isCycle);

        if (isCycle) {
            // Format the cycle line with reference notation
            // Use custom formatting for cycle nodes since they might be references
            String indentation = formatCycleIndentation(currentPath);
            String nodeStr = dumper.formatNode(currentPath);
            String line = indentation + nodeStr + " ^" + cycleIndex;
            consumer.accept(line);
            return false; // Stop traversing children to prevent infinite recursion
        }

        // Delegate to the wrapped dumper for normal nodes
        return dumper.visitEnter(node);
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        if (!currentPath.isEmpty() && currentPath.peek() == node) {
            currentPath.pop();
        }

        Boolean isCycle = isCycleStack.pop();

        // Only call dumper.visitLeave if we called dumper.visitEnter
        // (i.e., if it's not a cycle node)
        if (isCycle) {
            return true;
        }

        return dumper.visitLeave(node);
    }

    /**
     * Finds if the given node creates a cycle in the current path by checking if a node with the same
     * versionless artifact ID already exists in the path.
     *
     * @param node the node to check, must not be {@code null}
     * @return the index of the node in the current path that has the same versionless ID, or {@code -1} if no cycle
     */
    private int findCycleInPath(DependencyNode node) {
        Artifact currentArtifact = node.getArtifact();
        if (currentArtifact == null) {
            return -1;
        }

        int index = 0;
        for (DependencyNode pathNode : currentPath) {
            Artifact pathArtifact = pathNode.getArtifact();
            if (pathArtifact != null
                    && ArtifactIdUtils.equalsVersionlessId(currentArtifact, pathArtifact)) {
                return index; // Return the index of the node in the path (0-based, root is 0)
            }
            index++;
        }
        return -1; // No cycle found
    }

    /**
     * Formats the indentation for a cycle node. This is needed because cycle nodes
     * might not be in the parent's children list (they are references), so we need
     * to manually calculate the indentation based on the path structure.
     * <p>
     * This method uses the same logic as {@link DependencyGraphDumper#formatIndentation(Deque)},
     * but additionally handles cycle nodes that might be references by using artifact ID
     * comparison when identity comparison fails.
     * </p>
     *
     * @param path the current path including the cycle node
     * @return the indentation string for the cycle node
     */
    private String formatCycleIndentation(Deque<DependencyNode> path) {
        if (path.size() < 2) {
            return "";
        }

        StringBuilder buffer = new StringBuilder(128);
        Iterator<DependencyNode> iter = path.descendingIterator();
        DependencyNode parent = iter.hasNext() ? iter.next() : null;
        DependencyNode child = iter.hasNext() ? iter.next() : null;
        DependencyNode cycleNode = path.peekFirst(); // The cycle node is at the top

        while (parent != null && child != null) {
            boolean isLast = isLastChild(parent, child);
            boolean end = child == cycleNode;

            String indent = formatIndentString(isLast, end);
            buffer.append(indent);

            parent = child;
            child = iter.hasNext() ? iter.next() : null;
        }

        return buffer.toString();
    }

    /**
     * Determines if the given child is the last child of its parent.
     * For cycle nodes that might be references, uses artifact ID comparison
     * when identity comparison fails.
     *
     * @param parent the parent node
     * @param child the child node to check
     * @return {@code true} if the child is the last child, {@code false} otherwise
     */
    private boolean isLastChild(DependencyNode parent, DependencyNode child) {
        List<DependencyNode> children = parent.getChildren();
        if (children.isEmpty()) {
            return false;
        }

        DependencyNode lastChild = children.get(children.size() - 1);
        
        // Try identity comparison first (same as DependencyGraphDumper)
        if (lastChild == child) {
            return true;
        }

        // If identity fails, try artifact ID comparison (for cycle nodes that are references)
        Artifact childArtifact = child.getArtifact();
        if (childArtifact != null) {
            Artifact lastArtifact = lastChild.getArtifact();
            if (lastArtifact != null
                    && ArtifactIdUtils.equalsVersionlessId(childArtifact, lastArtifact)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Formats the indentation string for a single level.
     *
     * @param isLast whether this is the last child
     * @param isEnd whether this is the final node (cycle node)
     * @return the indentation string ("+- ", "\\- ", "|  ", or "   ")
     */
    private String formatIndentString(boolean isLast, boolean isEnd) {
        if (isEnd) {
            return isLast ? "\\- " : "+- ";
        } else {
            return isLast ? "   " : "|  ";
        }
    }
}

