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
package org.eclipse.aether.internal.impl.filter.ruletree;

import java.util.HashMap;
import java.util.function.Supplier;

/**
 * A tree structure with rules.
 */
abstract class Node<N extends Node<?>> {
    protected final String name;
    protected final HashMap<String, N> siblings;

    protected Node(String name) {
        this.name = name;
        this.siblings = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public boolean isLeaf() {
        return siblings.isEmpty();
    }

    protected N addSibling(String name, Supplier<N> supplier) {
        return siblings.computeIfAbsent(name, k -> supplier.get());
    }

    protected N getSibling(String name) {
        return siblings.get(name);
    }

    @Override
    public String toString() {
        return name;
    }

    public void dump(String prefix) {
        System.out.println(prefix + this);
        for (N node : siblings.values()) {
            node.dump(prefix + "  ");
        }
    }
}
