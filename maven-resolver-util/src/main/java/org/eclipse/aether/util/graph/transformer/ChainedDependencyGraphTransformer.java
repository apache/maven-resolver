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
package org.eclipse.aether.util.graph.transformer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that chains other transformers.
 */
public final class ChainedDependencyGraphTransformer implements DependencyGraphTransformer {

    private final DependencyGraphTransformer[] transformers;

    /**
     * Creates a new transformer that chains the specified transformers.
     *
     * @param transformers The transformers to chain, may be {@code null} or empty.
     */
    public ChainedDependencyGraphTransformer(DependencyGraphTransformer... transformers) {
        if (transformers == null) {
            this.transformers = new DependencyGraphTransformer[0];
        } else {
            this.transformers = transformers;
        }
    }

    /**
     * Creates a new transformer that chains the specified transformers or simply returns one of them if the other one
     * is {@code null}.
     *
     * @param transformer1 The first transformer of the chain, may be {@code null}.
     * @param transformer2 The second transformer of the chain, may be {@code null}.
     * @return The chained transformer or {@code null} if both input transformers are {@code null}.
     */
    public static DependencyGraphTransformer newInstance(
            DependencyGraphTransformer transformer1, DependencyGraphTransformer transformer2) {
        if (transformer1 == null) {
            return transformer2;
        } else if (transformer2 == null) {
            return transformer1;
        }
        return new ChainedDependencyGraphTransformer(transformer1, transformer2);
    }

    public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
            throws RepositoryException {
        requireNonNull(node, "node cannot be null");
        requireNonNull(context, "context cannot be null");
        for (DependencyGraphTransformer transformer : transformers) {
            node = transformer.transformGraph(node, context);
        }
        return node;
    }
}
