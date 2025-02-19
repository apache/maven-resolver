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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.version.VersionConstraint;

import static java.util.Objects.requireNonNull;

/**
 * A dependency visitor that dumps the graph to any {@link Consumer}{@code <String>}. Meant for diagnostic and testing, as
 * it may output the graph to standard output, error or even some logging interface.
 *
 * @since 1.9.8
 */
public class DependencyGraphDumper implements DependencyVisitor {
    /**
     * Decorator of "effective dependency": shows effective scope and optionality.
     */
    public static Function<DependencyNode, String> effectiveDependency() {
        return dependencyNode -> {
            Dependency d = dependencyNode.getDependency();
            if (d != null) {
                if (!d.getScope().isEmpty()) {
                    String result = d.getScope();
                    if (d.isOptional()) {
                        result += ", optional";
                    }
                    return "[" + result + "]";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "managed version": explains on nodes what was managed.
     */
    public static Function<DependencyNode, String> premanagedVersion() {
        return dependencyNode -> {
            if (dependencyNode.getArtifact() != null) {
                String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(dependencyNode);
                if (premanagedVersion != null
                        && !premanagedVersion.equals(
                                dependencyNode.getArtifact().getBaseVersion())) {
                    return "(version managed from " + premanagedVersion + ")";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "managed scope": explains on nodes what was managed.
     */
    public static Function<DependencyNode, String> premanagedScope() {
        return dependencyNode -> {
            Dependency d = dependencyNode.getDependency();
            if (d != null) {
                String premanagedScope = DependencyManagerUtils.getPremanagedScope(dependencyNode);
                if (premanagedScope != null && !premanagedScope.equals(d.getScope())) {
                    return "(scope managed from " + premanagedScope + ")";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "managed optionality": explains on nodes what was managed.
     */
    public static Function<DependencyNode, String> premanagedOptional() {
        return dependencyNode -> {
            Dependency d = dependencyNode.getDependency();
            if (d != null) {
                Boolean premanagedOptional = DependencyManagerUtils.getPremanagedOptional(dependencyNode);
                if (premanagedOptional != null && !premanagedOptional.equals(d.getOptional())) {
                    return "(optionality managed from " + premanagedOptional + ")";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "managed exclusions": explains on nodes what was managed.
     */
    public static Function<DependencyNode, String> premanagedExclusions() {
        return dependencyNode -> {
            Dependency d = dependencyNode.getDependency();
            if (d != null) {
                Collection<Exclusion> premanagedExclusions =
                        DependencyManagerUtils.getPremanagedExclusions(dependencyNode);
                if (premanagedExclusions != null && !equals(premanagedExclusions, d.getExclusions())) {
                    return "(exclusions managed from " + premanagedExclusions + ")";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "managed properties": explains on nodes what was managed.
     */
    public static Function<DependencyNode, String> premanagedProperties() {
        return dependencyNode -> {
            if (dependencyNode.getArtifact() != null) {
                Map<String, String> premanagedProperties =
                        DependencyManagerUtils.getPremanagedProperties(dependencyNode);
                if (premanagedProperties != null
                        && !equals(
                                premanagedProperties,
                                dependencyNode.getArtifact().getProperties())) {
                    return "(properties managed from " + premanagedProperties + ")";
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "range member": explains on nodes what range it participates in.
     */
    public static Function<DependencyNode, String> rangeMember() {
        return dependencyNode -> {
            VersionConstraint constraint = dependencyNode.getVersionConstraint();
            if (constraint != null && constraint.getRange() != null) {
                return "(range '" + constraint.getRange() + "')";
            }
            return null;
        };
    }
    /**
     * Decorator of "winner node": explains on losers why lost.
     */
    public static Function<DependencyNode, String> winnerNode() {
        return dependencyNode -> {
            if (dependencyNode.getArtifact() != null) {
                DependencyNode winner =
                        (DependencyNode) dependencyNode.getData().get(ConflictResolver.NODE_DATA_WINNER);
                if (winner != null) {
                    if (ArtifactIdUtils.equalsId(dependencyNode.getArtifact(), winner.getArtifact())) {
                        return "(nearer exists)";
                    } else {
                        Artifact w = winner.getArtifact();
                        String result = "conflicts with ";
                        if (ArtifactIdUtils.toVersionlessId(dependencyNode.getArtifact())
                                .equals(ArtifactIdUtils.toVersionlessId(w))) {
                            result += w.getVersion();
                        } else {
                            result += w;
                        }
                        return "(" + result + ")";
                    }
                }
            }
            return null;
        };
    }
    /**
     * Decorator of "artifact properties": prints out asked properties, if present.
     */
    public static Function<DependencyNode, String> artifactProperties(Collection<String> properties) {
        requireNonNull(properties, "properties");
        return dependencyNode -> {
            if (!properties.isEmpty() && dependencyNode.getDependency() != null) {
                String props = properties.stream()
                        .map(p -> p + "="
                                + dependencyNode.getDependency().getArtifact().getProperty(p, "n/a"))
                        .collect(Collectors.joining(","));
                if (!props.isEmpty()) {
                    return "(" + props + ")";
                }
            }
            return null;
        };
    }

    /**
     * The standard "default" decorators.
     *
     * @since 2.0.0
     */
    private static final List<Function<DependencyNode, String>> DEFAULT_DECORATORS =
            Collections.unmodifiableList(Arrays.asList(
                    effectiveDependency(),
                    premanagedVersion(),
                    premanagedScope(),
                    premanagedOptional(),
                    premanagedExclusions(),
                    premanagedProperties(),
                    rangeMember(),
                    winnerNode()));

    /**
     * Extends {@link #DEFAULT_DECORATORS} decorators with passed in ones.
     *
     * @since 2.0.0
     */
    public static List<Function<DependencyNode, String>> defaultsWith(
            Collection<Function<DependencyNode, String>> extras) {
        requireNonNull(extras, "extras");
        ArrayList<Function<DependencyNode, String>> result = new ArrayList<>(DEFAULT_DECORATORS);
        result.addAll(extras);
        return result;
    }

    private final Consumer<String> consumer;

    private final List<Function<DependencyNode, String>> decorators;

    private final Deque<DependencyNode> nodes = new ArrayDeque<>();

    /**
     * Creates instance with given consumer.
     *
     * @param consumer The string consumer, must not be {@code null}.
     */
    public DependencyGraphDumper(Consumer<String> consumer) {
        this(consumer, DEFAULT_DECORATORS);
    }

    /**
     * Creates instance with given consumer and decorators.
     *
     * @param consumer The string consumer, must not be {@code null}.
     * @param decorators The decorators to apply, must not be {@code null}.
     * @since 2.0.0
     */
    public DependencyGraphDumper(Consumer<String> consumer, Collection<Function<DependencyNode, String>> decorators) {
        this.consumer = requireNonNull(consumer);
        this.decorators = new ArrayList<>(decorators);
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
        for (Function<DependencyNode, String> decorator : decorators) {
            String decoration = decorator.apply(node);
            if (decoration != null) {
                buffer.append(" ").append(decoration);
            }
        }
        return buffer.toString();
    }

    private static boolean equals(Collection<Exclusion> c1, Collection<Exclusion> c2) {
        return c1 != null && c2 != null && c1.size() == c2.size() && c1.containsAll(c2);
    }

    private static boolean equals(Map<String, String> m1, Map<String, String> m2) {
        return m1 != null
                && m2 != null
                && m1.size() == m2.size()
                && m1.entrySet().stream().allMatch(entry -> Objects.equals(m2.get(entry.getKey()), entry.getValue()));
    }
}
