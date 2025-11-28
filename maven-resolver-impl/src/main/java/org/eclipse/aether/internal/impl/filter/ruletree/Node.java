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

/**
 * A tree structure with rules.
 */
class Node {
    private final String name;
    private final boolean stop;
    private Boolean allow;
    private final HashMap<String, Node> siblings;

    protected Node(String name, boolean stop, Boolean allow) {
        this.name = name;
        this.stop = stop;
        this.allow = allow;
        this.siblings = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public boolean isLeaf() {
        return siblings.isEmpty();
    }

    public boolean isStop() {
        return stop;
    }

    public Boolean isAllow() {
        return allow;
    }

    public void setAllow(Boolean allow) {
        this.allow = allow;
    }

    protected Node addSibling(String name, boolean stop, Boolean allow) {
        return siblings.computeIfAbsent(name, n -> new Node(n, stop, allow));
    }

    protected Node getSibling(String name) {
        return siblings.get(name);
    }

    @Override
    public String toString() {
        return (allow != null ? (allow ? "+" : "-") : "?") + (stop ? "=" : "") + name;
    }

    public void dump(String prefix) {
        System.out.println(prefix + this);
        for (Node node : siblings.values()) {
            node.dump(prefix + "  ");
        }
    }
}
