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
package org.eclipse.aether.graph;

/**
 * A visitor for nodes of the dependency graph.
 *
 * @see DependencyNode#accept(DependencyVisitor)
 */
public interface DependencyVisitor {

    /**
     * Notifies the visitor of a node visit before its children have been processed.
     *
     * @param node The dependency node being visited, must not be {@code null}.
     * @return {@code true} to visit child nodes of the specified node as well, {@code false} to skip children.
     */
    boolean visitEnter(DependencyNode node);

    /**
     * Notifies the visitor of a node visit after its children have been processed. Note that this method is always
     * invoked regardless whether any children have actually been visited.
     *
     * @param node The dependency node being visited, must not be {@code null}.
     * @return {@code true} to visit siblings nodes of the specified node as well, {@code false} to skip siblings.
     */
    boolean visitLeave(DependencyNode node);
}
