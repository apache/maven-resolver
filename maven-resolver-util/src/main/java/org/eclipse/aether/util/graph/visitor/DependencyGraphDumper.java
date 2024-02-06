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
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import static java.util.Objects.requireNonNull;

/**
 * A dependency visitor that dumps the graph to any {@link Consumer}{@code <String>}. Meant for diagnostic and testing, as
 * it may output the graph to standard output, error or even some logging interface.
 *
 * @since 1.9.8
 */
public class DependencyGraphDumper implements DependencyVisitor {

    private final Consumer<String> consumer;

    private final Deque<DependencyNode> nodes = new ArrayDeque<>();

    public DependencyGraphDumper(Consumer<String> consumer) {
        this.consumer = requireNonNull(consumer);
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        nodes.push(node);
        consumer.accept(formatLine(nodes));
        return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        if (!nodes.isEmpty()) {
            nodes.pop();
        }
        return true;
    }

    protected String formatLine(Deque<DependencyNode> nodes) {
        return formatIndentation(nodes) + formatNode(nodes);
    }

    protected String formatIndentation(Deque<DependencyNode> nodes) {
        StringBuilder buffer = new StringBuilder(128);
        Iterator<DependencyNode> iter = nodes.descendingIterator();
        DependencyNode parent = iter.hasNext() ? iter.next() : null;
        DependencyNode child = iter.hasNext() ? iter.next() : null;
        while (parent != null && child != null) {
            boolean lastChild = parent.getChildren().get(parent.getChildren().size() - 1) == child;
            boolean end = child == nodes.peekFirst();
            String indent;
            if (end) {
                indent = lastChild ? "\\- " : "+- ";
            } else {
                indent = lastChild ? "   " : "|  ";
            }
            buffer.append(indent);
            parent = child;
            child = iter.hasNext() ? iter.next() : null;
        }
        return buffer.toString();
    }

    protected String formatNode(Deque<DependencyNode> nodes) {
        DependencyNode node = requireNonNull(nodes.peek(), "bug: should not happen");
        StringBuilder buffer = new StringBuilder(128);
        Artifact a = node.getArtifact();
        buffer.append(a);
        Dependency d = node.getDependency();
        if (d != null && !d.getScope().isEmpty()) {
            buffer.append(" [").append(d.getScope());
            if (d.isOptional()) {
                buffer.append(", optional");
            }
            buffer.append("]");
        }
        String premanaged = DependencyManagerUtils.getPremanagedVersion(node);
        if (premanaged != null && !premanaged.equals(a.getBaseVersion())) {
            buffer.append(" (version managed from ").append(premanaged).append(")");
        }

        premanaged = DependencyManagerUtils.getPremanagedScope(node);
        if (premanaged != null && d != null && !premanaged.equals(d.getScope())) {
            buffer.append(" (scope managed from ").append(premanaged).append(")");
        }

        Boolean premanagedOptional = DependencyManagerUtils.getPremanagedOptional(node);
        if (premanagedOptional != null && d != null && !premanagedOptional.equals(d.getOptional())) {
            buffer.append(" (optionality managed from ")
                    .append(premanagedOptional)
                    .append(")");
        }

        Collection<Exclusion> premanagedExclusions = DependencyManagerUtils.getPremanagedExclusions(node);
        if (premanagedExclusions != null && d != null && !equals(premanagedExclusions, d.getExclusions())) {
            buffer.append(" (exclusions managed from ")
                    .append(premanagedExclusions)
                    .append(")");
        }

        Map<String, String> premanagedProperties = DependencyManagerUtils.getPremanagedProperties(node);
        if (premanagedProperties != null && !equals(premanagedProperties, a.getProperties())) {
            buffer.append(" (properties managed from ")
                    .append(premanagedProperties)
                    .append(")");
        }

        DependencyNode winner = (DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        if (winner != null) {
            if (ArtifactIdUtils.equalsId(a, winner.getArtifact())) {
                buffer.append(" (nearer exists)");
            } else {
                Artifact w = winner.getArtifact();
                buffer.append(" (conflicts with ");
                if (ArtifactIdUtils.toVersionlessId(a).equals(ArtifactIdUtils.toVersionlessId(w))) {
                    buffer.append(w.getVersion());
                } else {
                    buffer.append(w);
                }
                buffer.append(")");
            }
        }
        return buffer.toString();
    }

    private boolean equals(Collection<Exclusion> c1, Collection<Exclusion> c2) {
        return c1 != null && c2 != null && c1.size() == c2.size() && c1.containsAll(c2);
    }

    private boolean equals(Map<String, String> m1, Map<String, String> m2) {
        return m1 != null
                && m2 != null
                && m1.size() == m2.size()
                && m1.entrySet().stream().allMatch(entry -> Objects.equals(m2.get(entry.getKey()), entry.getValue()));
    }
}
