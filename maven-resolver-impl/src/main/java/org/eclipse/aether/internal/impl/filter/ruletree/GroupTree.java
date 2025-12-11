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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Group tree for Maven groupIDs.
 * This class parses a text file that has a directive on each line. Directive examples:
 * <ul>
 *     <li>ignored/formatting - each line starting with {@code '#'} (hash) or being empty/blank is ignored.</li>
 *     <li>modifier {@code !} is negation (disallow; by def entry allows). If present must be first character.</li>
 *     <li>modifier {@code =} is limiter (to given G; by def is "G and below"). If present, must be first character. If negation present, must be second character.</li>
 *     <li>a valid Maven groupID ie "org.apache.maven".</li>
 * </ul>
 * By default, a G entry ie {@code org.apache.maven} means "allow {@code org.apache.maven} G and all Gs below
 * (so {@code org.apache.maven.plugins} etc. are all allowed). There is one special entry {@code "*"} (asterisk)
 * that means "root" and defines the default acceptance: {@code "*"} means "by default accept" and {@code "!*"}
 * means "by default deny" (same effect as when this character is not present in file). Use of limiter modifier
 * on "root" like {@code "=*"} has no effect, is simply ignored.
 *
 * <p>
 * Examples:
 * <pre>
 * {@code
 * # this is my group filter list
 *
 * org.apache.maven
 * !=org.apache.maven.foo
 * !org.apache.maven.indexer
 * =org.apache.bar
 * }
 * </pre>
 *
 * File meaning: "allow all {@code org.apache.maven} and below", "disallow {@code org.apache.maven.foo} groupId ONLY"
 * (hence {@code org.apache.maven.foo.bar} is allowed due first line), "disallow {@code org.apache.maven.indexer} and below"
 * and "allow {@code org.apache.bar} groupID ONLY".
 *
 * <p>
 * In case of conflicting rules, parsing happens by "first wins", so line closer to first line in file "wins", and conflicting
 * line is ignored.
 */
public class GroupTree extends Node<GroupTree> {
    /**
     * Creates root (special for root: allow is not null).
     */
    public static GroupTree create(String name) {
        GroupTree result = new GroupTree(name);
        result.allow = false;
        return result;
    }

    private static final String ROOT = "*";
    private static final String MOD_EXCLUSION = "!";
    private static final String MOD_STOP = "=";

    private static List<String> elementsOfGroup(final String groupId) {
        return Arrays.stream(groupId.split("\\.")).filter(e -> !e.isEmpty()).collect(toList());
    }

    private boolean stop;
    private Boolean allow;

    private GroupTree(String name) {
        super(name);
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
            GroupTree currentNode = this;
            boolean allow = true;
            if (line.startsWith(MOD_EXCLUSION)) {
                allow = false;
                line = line.substring(MOD_EXCLUSION.length());
            }
            boolean stop = false;
            if (line.startsWith(MOD_STOP)) {
                stop = true;
                line = line.substring(MOD_STOP.length());
            }
            if (ROOT.equals(line)) {
                this.allow = allow;
                return true;
            }
            List<String> groupElements = elementsOfGroup(line);
            for (String groupElement : groupElements.subList(0, groupElements.size() - 1)) {
                currentNode = currentNode.siblings.computeIfAbsent(groupElement, GroupTree::new);
            }
            String lastElement = groupElements.get(groupElements.size() - 1);
            currentNode = currentNode.siblings.computeIfAbsent(lastElement, GroupTree::new);
            currentNode.stop = stop;
            currentNode.allow = allow;
            return true;
        }
        return false;
    }

    public boolean acceptedGroupId(String groupId) {
        final List<String> current = new ArrayList<>();
        final List<String> groupElements = elementsOfGroup(groupId);
        Boolean accepted = null;
        GroupTree currentNode = this;
        for (String groupElement : groupElements) {
            current.add(groupElement);
            currentNode = currentNode.siblings.get(groupElement);
            if (currentNode == null) {
                // we stepped off the tree; use value we got so far
                break;
            } else if (currentNode.stop && groupElements.equals(current)) {
                // exact match
                accepted = currentNode.allow;
                break;
            } else if (!currentNode.stop && currentNode.allow != null) {
                // "inherit" if not STOP and allow is set; and most probably we loop more
                accepted = currentNode.allow;
            }
        }
        // use 'accepted', if defined; otherwise fallback to root (it always has 'allow' set)
        return accepted != null ? accepted : this.allow;
    }

    @Override
    public String toString() {
        return (allow != null ? (allow ? "+" : "-") : "?") + (stop ? "=" : "") + name;
    }

    @Override
    public void dump(String prefix) {
        System.out.println(prefix + this);
        for (GroupTree node : siblings.values()) {
            node.dump(prefix + "  ");
        }
    }
}
