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

import java.util.function.Consumer;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Processes dependency graph by traversing the graph in preorder. This visitor visits each node exactly once
 * regardless how many paths within the dependency graph lead to the node such that the resulting node sequence is
 * free of duplicates.
 * <p>
 * <strong>Instances of this class cannot be embedded into {@link FilteringDependencyVisitor}</strong>, pass in the
 * filter {@link DependencyFilter} into  constructor instead.
 *
 * @see NodeListGenerator
 * @since 2.0.0
 */
public final class PreorderDependencyNodeConsumerVisitor extends AbstractDependencyNodeConsumerVisitor {

    public static final String NAME = ConfigurationProperties.REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_PREORDER;

    /**
     * Creates a new preorder list generator.
     */
    public PreorderDependencyNodeConsumerVisitor(Consumer<DependencyNode> nodeConsumer) {
        this(nodeConsumer, null);
    }

    /**
     * Creates a new preorder list generator.
     *
     * @since 2.0.12
     */
    public PreorderDependencyNodeConsumerVisitor(Consumer<DependencyNode> nodeConsumer, DependencyFilter filter) {
        super(nodeConsumer, filter);
    }

    @Override
    protected boolean doVisitEnter(DependencyNode node) {
        if (!setVisited(node)) {
            return false;
        }
        if (acceptNode(node)) {
            consumeNode(node);
        }
        return true;
    }

    @Override
    protected boolean doVisitLeave(DependencyNode node) {
        return true;
    }
}
