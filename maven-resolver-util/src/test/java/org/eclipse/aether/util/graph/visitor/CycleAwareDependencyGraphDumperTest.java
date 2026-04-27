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
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CycleAwareDependencyGraphDumperTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/ordered-list/").parseResource(resource);
    }

    @Test
    void dumpSimple() throws Exception {
        DependencyNode root = parse("simple.txt");

        List<String> cycleAwareOutput = new ArrayList<>();
        root.accept(new CycleAwareDependencyGraphDumper(cycleAwareOutput::add));

        List<String> standardOutput = new ArrayList<>();
        root.accept(new DependencyGraphDumper(standardOutput::add));

        // For graphs without cycles, output should be identical
        assertEquals(standardOutput.size(), cycleAwareOutput.size());
        for (int i = 0; i < standardOutput.size(); i++) {
            assertEquals(standardOutput.get(i), cycleAwareOutput.get(i));
        }
    }

    @Test
    void dumpCycles() throws Exception {
        DependencyNode root = parse("cycles.txt");

        List<String> output = new ArrayList<>();
        root.accept(new CycleAwareDependencyGraphDumper(output::add));

        assertFalse(output.isEmpty());
        assertTrue(output.stream().anyMatch(line -> line.contains("^")));
        assertTrue(output.stream().anyMatch(line -> line.matches(".*\\^\\d+.*")));
    }

    @Test
    void dumpCyclesNoStackOverflow() throws Exception {
        DependencyNode root = parse("cycles.txt");
        List<String> output = new ArrayList<>();
        assertDoesNotThrow(() -> root.accept(new CycleAwareDependencyGraphDumper(output::add)));
        assertFalse(output.isEmpty());
    }

    @Test
    void cycleReferencePointsToCorrectIndex() throws Exception {
        DependencyNode root = parse("cycles.txt");
        List<String> output = new ArrayList<>();
        root.accept(new CycleAwareDependencyGraphDumper(output::add));

        String cycleLine = output.stream()
                .filter(line -> line.contains("^"))
                .findFirst()
                .orElse(null);

        assertNotNull(cycleLine);
        int cycleIndex = extractCycleIndex(cycleLine);
        assertTrue(cycleIndex >= 0);
        assertTrue(cycleIndex < output.size());
    }

    private int extractCycleIndex(String line) {
        int caretIndex = line.indexOf('^');
        if (caretIndex < 0) {
            return -1;
        }
        String afterCaret = line.substring(caretIndex + 1).trim();
        try {
            return Integer.parseInt(afterCaret.split("\\s")[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

