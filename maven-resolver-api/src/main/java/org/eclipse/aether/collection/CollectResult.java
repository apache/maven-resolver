package org.eclipse.aether.collection;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;

/**
 * The result of a dependency collection request.
 * 
 * @see RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)
 */
public final class CollectResult
{

    private final CollectRequest request;

    private List<Exception> exceptions;

    private List<DependencyCycle> cycles;

    private DependencyNode root;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The resolution request, must not be {@code null}.
     */
    public CollectResult( CollectRequest request )
    {
        this.request = requireNonNull( request, "dependency collection request cannot be null" );
        exceptions = Collections.emptyList();
        cycles = Collections.emptyList();
    }

    /**
     * Gets the collection request that was made.
     *
     * @return The collection request, never {@code null}.
     */
    public CollectRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the exceptions that occurred while building the dependency graph.
     * 
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Records the specified exception while building the dependency graph.
     * 
     * @param exception The exception to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public CollectResult addException( Exception exception )
    {
        if ( exception != null )
        {
            if ( exceptions.isEmpty() )
            {
                exceptions = new ArrayList<>();
            }
            exceptions.add( exception );
        }
        return this;
    }

    /**
     * Gets the dependency cycles that were encountered while building the dependency graph.
     * 
     * @return The dependency cycles in the (raw) graph, never {@code null}.
     */
    public List<DependencyCycle> getCycles()
    {
        return cycles;
    }

    /**
     * Records the specified dependency cycle.
     * 
     * @param cycle The dependency cycle to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public CollectResult addCycle( DependencyCycle cycle )
    {
        if ( cycle != null )
        {
            if ( cycles.isEmpty() )
            {
                cycles = new ArrayList<>();
            }
            cycles.add( cycle );
        }
        return this;
    }

    /**
     * Gets the root node of the dependency graph.
     * 
     * @return The root node of the dependency graph or {@code null} if none.
     */
    public DependencyNode getRoot()
    {
        return root;
    }

    /**
     * Sets the root node of the dependency graph.
     * 
     * @param root The root node of the dependency graph, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public CollectResult setRoot( DependencyNode root )
    {
        this.root = root;
        return this;
    }

    @Override
    public String toString()
    {
        return String.valueOf( getRoot() );
    }

}
