/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.collection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;

/**
 * The result of a dependency collection request.
 * 
 * @see RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)
 */
public final class CollectResult
{

    private final CollectRequest request;

    private final List<Exception> exceptions;

    private DependencyNode root;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The resolution request, must not be {@code null}.
     */
    public CollectResult( CollectRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "dependency collection request has not been specified" );
        }
        this.request = request;
        this.exceptions = new ArrayList<Exception>( 4 );
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
            this.exceptions.add( exception );
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
