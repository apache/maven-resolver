package org.apache.maven.resolver.internal.impl.collect;

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

import java.util.List;

import org.apache.maven.resolver.collection.CollectStepData;
import org.apache.maven.resolver.graph.Dependency;
import org.apache.maven.resolver.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * Trace objects for dependency collection.
 *
 * @since 1.8.1
 */
public final class CollectStepDataImpl implements CollectStepData
{
    private final String context;

    private final List<DependencyNode> path;

    private final Dependency node;

    public CollectStepDataImpl( final String context, final List<DependencyNode> path, final Dependency node )
    {
        this.context = requireNonNull( context );
        this.path = requireNonNull( path );
        this.node = requireNonNull( node );
    }

    @Override
    public String getContext()
    {
        return context;
    }

    @Override
    public List<DependencyNode> getPath()
    {
        return path;
    }

    @Override
    public Dependency getNode()
    {
        return node;
    }
}
