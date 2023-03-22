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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import static java.util.Objects.requireNonNull;

/**
 * A dependency visitor that dumps the graph to any {@link Consumer<String>}. Meant for diagnostic and testing, as
 * it may output the graph to standard output, error or even some logging interface.
 */
public class DependencyGraphDumper implements DependencyVisitor {

    private final Consumer<String> consumer;

    private final List<ChildInfo> childInfos = new ArrayList<>();

    public DependencyGraphDumper(Consumer<String> consumer) {
        this.consumer = requireNonNull(consumer);
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        consumer.accept(formatIndentation() + formatNode(node));
        childInfos.add(new ChildInfo(node.getChildren().size()));
        return true;
    }

    private String formatIndentation() {
        StringBuilder buffer = new StringBuilder(128);
        for (Iterator<ChildInfo> it = childInfos.iterator(); it.hasNext(); ) {
            buffer.append(it.next().formatIndentation(!it.hasNext()));
        }
        return buffer.toString();
    }

    private String formatNode(DependencyNode node) {
        StringBuilder buffer = new StringBuilder(128);
        Artifact a = node.getArtifact();
        Dependency d = node.getDependency();
        buffer.append(a);
        if (d != null && d.getScope().length() > 0) {
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
        if (premanaged != null && !premanaged.equals(d.getScope())) {
            buffer.append(" (scope managed from ").append(premanaged).append(")");
        }
        DependencyNode winner = (DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
        if (winner != null && !ArtifactIdUtils.equalsId(a, winner.getArtifact())) {
            Artifact w = winner.getArtifact();
            buffer.append(" (conflicts with ");
            if (ArtifactIdUtils.toVersionlessId(a).equals(ArtifactIdUtils.toVersionlessId(w))) {
                buffer.append(w.getVersion());
            } else {
                buffer.append(w);
            }
            buffer.append(")");
        }
        return buffer.toString();
    }

    public boolean visitLeave(DependencyNode node) {
        if (!childInfos.isEmpty()) {
            childInfos.remove(childInfos.size() - 1);
        }
        if (!childInfos.isEmpty()) {
            childInfos.get(childInfos.size() - 1).index++;
        }
        return true;
    }

    private static class ChildInfo {

        final int count;

        int index;

        ChildInfo(int count) {
            this.count = count;
        }

        public String formatIndentation(boolean end) {
            boolean last = index + 1 >= count;
            if (end) {
                return last ? "\\- " : "+- ";
            }
            return last ? "   " : "|  ";
        }
    }
}
