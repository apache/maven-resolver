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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Prefix tree for paths: if you step on a path prefix that exists, you are good to go.
 * This class parses a text file that has a prefix on each line:
 * <ul>
 *     <li>ignored/formatting - each line starting with {@code '#'} (hash) or being empty/blank is ignored.</li>
 *     <li>a path prefix  ie "/org/apache/maven".</li>
 * </ul>
 * By default, artifact is allowed if layout converted path of it has a matching prefix in this file.
 *
 * <p>
 * Example prefix files:
 * <ul>
 *     <li><a href="https://repo.maven.apache.org/maven2/.meta/prefixes.txt">Maven Central</a></li>
 *     <li><a href="https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt">ASF Releases</a></li>
 *     <li><a href="https://repo.eclipse.org/content/repositories/tycho/.meta/prefixes.txt">Eclipse Tycho</a></li>
 * </ul>
 */
public class PrefixTree {
    private static List<String> elementsOfPath(final String path) {
        return Arrays.stream(path.split("/")).filter(e -> !e.isEmpty()).collect(toList());
    }

    private final String name;
    private final HashMap<String, PrefixTree> children;
    private final boolean leaf;

    public PrefixTree(String name) {
        this(name, false);
    }

    private PrefixTree(String name, boolean leaf) {
        this.name = name;
        this.children = new HashMap<>();
        this.leaf = leaf;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public int loadNodes(Stream<String> linesStream) {
        AtomicInteger counter = new AtomicInteger(0);
        linesStream.forEach(line -> {
            if (loadNode(line)) {
                counter.incrementAndGet();
            }
        });
        return counter.get();
    }

    /**
     * Adds a line to the tree:
     * <ul>
     *     <li>it parses the URI-path-like element into list of segments {@link #elementsOfPath(String)}</li>
     *     <li>cycles over the list, and marks last node added as {@code leaf=true}</li>
     *     <li>if already on leaf node, processing stops (shorter path wins)</li>
     * </ul>
     */
    public boolean loadNode(String line) {
        if (!line.startsWith("#") && !line.trim().isEmpty()) {
            final List<String> elements = elementsOfPath(line);
            final int lastElementIdx = elements.size() - 1;
            PrefixTree currentNode = this;
            for (int i = 0; i <= lastElementIdx; i++) {
                String element = elements.get(i);
                boolean leaf = i == lastElementIdx;
                PrefixTree siblingNode = currentNode.children.get(element);
                if (leaf && siblingNode != null && !siblingNode.isLeaf()) {
                    // shorter wins regardless of load order: we got /a/b/c, now /a/b arrives, drop the subtree
                    siblingNode = new PrefixTree(element, true);
                    currentNode.children.put(element, siblingNode);
                }
                if (siblingNode == null) {
                    if (currentNode.isLeaf()) {
                        break; // shorter wins: we got /a/b/c, but we already had /a/b
                    } else {
                        siblingNode = new PrefixTree(element, leaf);
                        currentNode.children.put(element, siblingNode);
                    }
                }
                currentNode = siblingNode;
            }
            return true;
        }
        return false;
    }

    public boolean acceptedPath(String path) {
        final List<String> pathElements = elementsOfPath(path);
        PrefixTree currentNode = this;
        for (String pathElement : pathElements) {
            currentNode = currentNode.children.get(pathElement);
            if (currentNode == null || currentNode.isLeaf()) {
                break;
            }
        }
        // path is accepted if its elements lead to an existing node that is a leaf node
        return currentNode != null && currentNode.isLeaf();
    }
}
