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
 * Example prefix files:
 * <ul>
 *     <li><a href="https://repo.maven.apache.org/maven2/.meta/prefixes.txt">Maven Central</a></li>
 *     <li><a href="https://repository.apache.org/content/repositories/releases/.meta/prefixes.txt">ASF Releases</a></li>
 *     <li><a href="https://repo.eclipse.org/content/repositories/tycho/.meta/prefixes.txt">Eclipse Tycho</a></li>
 * </ul>
 */
public class PrefixTree extends Node {
    private static List<String> elementsOfPath(final String path) {
        return Arrays.stream(path.split("/")).filter(e -> !e.isEmpty()).collect(toList());
    }

    public PrefixTree(String name) {
        super(name, false, null);
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

    public boolean loadNode(String line) {
        if (!line.startsWith("#") && !line.trim().isEmpty()) {
            Node currentNode = this;
            for (String element : elementsOfPath(line)) {
                currentNode = currentNode.addSibling(element, false, null);
            }
            return true;
        }
        return false;
    }

    public boolean acceptedPath(String path) {
        final List<String> pathElements = elementsOfPath(path);
        Node currentNode = this;
        for (String pathElement : pathElements) {
            currentNode = currentNode.getSibling(pathElement);
            if (currentNode == null || currentNode.isLeaf()) {
                break;
            }
        }
        return currentNode != null && currentNode.isLeaf();
    }
}
