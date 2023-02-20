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
package org.eclipse.aether.internal.impl.collect.df;

import java.util.ArrayList;

import org.eclipse.aether.graph.DependencyNode;

/**
 * Internal helper for {@link DfDependencyCollector}. Originally (pre-1.8.0) this same class was located a
 * package higher.
 *
 * @since 1.8.0
 */
final class NodeStack {

    @SuppressWarnings({"checkstyle:magicnumber"})
    // CHECKSTYLE_OFF: MagicNumber
    ArrayList<DependencyNode> nodes = new ArrayList<>(96);
    // CHECKSTYLE_ON: MagicNumber

    public DependencyNode top() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("stack empty");
        }
        return nodes.get(nodes.size() - 1);
    }

    public void push(DependencyNode node) {
        nodes.add(node);
    }

    public void pop() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("stack empty");
        }
        nodes.remove(nodes.size() - 1);
    }

    public int size() {
        return nodes.size();
    }

    public DependencyNode get(int index) {
        return nodes.get(index);
    }

    @Override
    public String toString() {
        return nodes.toString();
    }
}
