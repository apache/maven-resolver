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

import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

public class FilteringDependencyVisitorTest {

    private DependencyNode parse(String resource) throws Exception {
        return new DependencyGraphParser("visitor/filtering/").parseResource(resource);
    }

    @Test
    public void testFilterCalledWithProperParentStack() throws Exception {
        DependencyNode root = parse("parents.txt");

        final StringBuilder buffer = new StringBuilder(256);
        DependencyFilter filter = new DependencyFilter() {
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                requireNonNull(node, "node cannot be null");
                requireNonNull(parents, "parents cannot be null");
                for (DependencyNode parent : parents) {
                    buffer.append(parent.getDependency().getArtifact().getArtifactId());
                }
                buffer.append(",");
                return false;
            }
        };

        FilteringDependencyVisitor visitor = new FilteringDependencyVisitor(new PreorderNodeListGenerator(), filter);
        root.accept(visitor);

        assertEquals(",a,ba,cba,a,ea,", buffer.toString());
    }
}
