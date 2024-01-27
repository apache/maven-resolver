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

import java.io.File;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractDepthFirstNodeListGeneratorTestSupport {

    protected DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/ordered-list/").parseResource(resource);
    }

    protected void assertSequence(List<DependencyNode> actual, String... expected) {
        assertEquals(actual.toString(), expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            DependencyNode node = actual.get(i);
            assertEquals(
                    actual.toString(),
                    expected[i],
                    node.getDependency().getArtifact().getArtifactId());
        }
    }

    @Test
    public void testClasspath() throws Exception {
        DependencyNode root = parse("simple.txt");

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept(visitor);

        assertEquals(visitor.getClassPath(), "");
    }

    @Test
    public void testFullyResolverClasspath() throws Exception {
        DependencyNode root = parse("simple.txt");

        DependencyVisitor fileSetter = new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                node.setArtifact(
                        node.getArtifact().setFile(new File(node.getArtifact().getArtifactId())));
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                return true;
            }
        };
        root.accept(fileSetter);

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept(visitor);

        String classpath = visitor.getClassPath();
        assertEquals(5, classpath.split(File.pathSeparator).length);
        for (File file : visitor.getFiles()) {
            assertTrue("missing: " + file, classpath.contains(file.getAbsolutePath()));
        }
    }
}
