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
package org.eclipse.aether.collection;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Transforms a given dependency graph.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 * <p>
 * <em>Warning:</em> Dependency graphs may generally contain cycles. As such a graph transformer that cannot assume for
 * sure that cycles have already been eliminated must gracefully handle cyclic graphs, e.g. guard against infinite
 * recursion.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getDependencyGraphTransformer()
 */
public interface DependencyGraphTransformer {

    /**
     * Transforms the dependency graph denoted by the specified root node. The transformer may directly change the
     * provided input graph or create a new graph, the former is recommended for performance reasons.
     *
     * @param node The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
     * @param context The graph transformation context, must not be {@code null}.
     * @return The result graph of the transformation, never {@code null}.
     * @throws RepositoryException If the transformation failed.
     */
    DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException;
}
