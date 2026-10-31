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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class CycleAwareDependencyGraphDumperTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/ordered-list/").parseResource(resource);
    }

    @Test
    void dumpCyclesDoesNotRecurseForever() throws Exception {
        DependencyNode root = parse("cycles.txt");
        List<String> lines = new ArrayList<>();
        assertTimeoutPreemptively(
                Duration.ofSeconds(10), () -> root.accept(new CycleAwareDependencyGraphDumper(lines::add)));

        assertEquals(
                Arrays.asList(
                        "gid:a:jar:1",
                        "+- gid:b:jar:1",
                        "|  \\- gid:c:jar:1",
                        "|     \\- gid:b:jar:1 (cycle)",
                        "\\- gid:d:jar:1",
                        "   +- gid:b:jar:1",
                        "   |  \\- gid:c:jar:1",
                        "   |     \\- gid:b:jar:1 (cycle)",
                        "   \\- gid:e:jar:1"),
                lines);
    }

    @Test
    void dumpSimpleMatchesPlainDumper() throws Exception {
        DependencyNode root = parse("simple.txt");
        List<String> plain = new ArrayList<>();
        List<String> cycleAware = new ArrayList<>();
        root.accept(new DependencyGraphDumper(plain::add));
        root.accept(new CycleAwareDependencyGraphDumper(cycleAware::add));

        assertEquals(plain, cycleAware);
    }
}
