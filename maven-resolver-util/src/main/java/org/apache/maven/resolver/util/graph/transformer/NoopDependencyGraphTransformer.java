package org.apache.maven.resolver.util.graph.transformer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.resolver.RepositoryException;
import org.apache.maven.resolver.collection.DependencyGraphTransformationContext;
import org.apache.maven.resolver.collection.DependencyGraphTransformer;
import org.apache.maven.resolver.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A dependency graph transformer that does not perform any changes on its input.
 */
public final class NoopDependencyGraphTransformer
    implements DependencyGraphTransformer
{

    /**
     * A ready-made instance of this dependency graph transformer which can safely be reused throughout an entire
     * application regardless of multi-threading.
     */
    public static final DependencyGraphTransformer INSTANCE = new NoopDependencyGraphTransformer();

    /**
     * Creates a new instance of this graph transformer. Usually, {@link #INSTANCE} should be used instead.
     */
    public NoopDependencyGraphTransformer()
    {
    }

    public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        requireNonNull( node, "node cannot be null" );
        requireNonNull( context, "context cannot be null" );
        return node;
    }

}
