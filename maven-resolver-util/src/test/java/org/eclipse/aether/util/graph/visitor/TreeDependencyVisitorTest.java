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

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TreeDependencyVisitorTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/tree/").parseResource(resource);
    }

    @Test
    void testDuplicateSuppression() throws Exception {
        DependencyNode root = parse("cycles.txt");

        RecordingVisitor rec = new RecordingVisitor();
        TreeDependencyVisitor visitor = new TreeDependencyVisitor(rec);
        root.accept(visitor);

        assertEquals(">a >b >c <c <b >d <d <a ", rec.buffer.toString());
    }

    private static class RecordingVisitor implements DependencyVisitor {

        StringBuilder buffer = new StringBuilder(256);

        public boolean visitEnter(DependencyNode node) {
            buffer.append('>')
                    .append(node.getDependency().getArtifact().getArtifactId())
                    .append(' ');
            return true;
        }

        public boolean visitLeave(DependencyNode node) {
            buffer.append('<')
                    .append(node.getDependency().getArtifact().getArtifactId())
                    .append(' ');
            return true;
        }
    }
}
