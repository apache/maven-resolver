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
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to collect the transitive dependencies and to build a dependency graph from them. There are three ways to
 * create a dependency graph. First, only the root dependency can be given. Second, a root dependency and direct
 * dependencies can be specified in which case the specified direct dependencies are merged with the direct dependencies
 * retrieved from the artifact descriptor of the root dependency. And last, only direct dependencies can be specified in
 * which case the root node of the resulting graph has no associated dependency.
 * 
 * @see RepositorySystem#collectDependencies(RepositorySystemSession, CollectRequest)
 */
public final class CollectRequest
{

    private Dependency root;

    private List<Dependency> dependencies = Collections.emptyList();

    private List<Dependency> managedDependencies = Collections.emptyList();

    private List<RemoteRepository> repositories = Collections.emptyList();

    private String context = "";

    private RequestTrace trace;

    /**
     * Creates an unitialized request.
     */
    public CollectRequest()
    {
        // enables default constructor
    }

    /**
     * Creates a request with the specified properties.
     * 
     * @param root The root dependency whose transitive dependencies should be collected, may be {@code null}.
     * @param repositories The repositories to use for the collection, may be {@code null}.
     */
    public CollectRequest( Dependency root, List<RemoteRepository> repositories )
    {
        setRoot( root );
        setRepositories( repositories );
    }

    /**
     * Creates a new request with the specified properties.
     * 
     * @param root The root dependency whose transitive dependencies should be collected, may be {@code null}.
     * @param dependencies The direct dependencies to merge with the direct dependencies from the root dependency's
     *            artifact descriptor.
     * @param repositories The repositories to use for the collection, may be {@code null}.
     */
    public CollectRequest( Dependency root, List<Dependency> dependencies, List<RemoteRepository> repositories )
    {
        setRoot( root );
        setDependencies( dependencies );
        setRepositories( repositories );
    }

    /**
     * Creates a new request with the specified properties.
     * 
     * @param dependencies The direct dependencies of some imaginary root, may be {@code null}.
     * @param managedDependencies The dependency management information to apply to the transitive dependencies, may be
     *            {@code null}.
     * @param repositories The repositories to use for the collection, may be {@code null}.
     */
    public CollectRequest( List<Dependency> dependencies, List<Dependency> managedDependencies,
                           List<RemoteRepository> repositories )
    {
        setDependencies( dependencies );
        setManagedDependencies( managedDependencies );
        setRepositories( repositories );
    }

    /**
     * Gets the root dependency of the graph.
     * 
     * @return The root dependency of the graph or {@code null} if none.
     */
    public Dependency getRoot()
    {
        return root;
    }

    /**
     * Sets the root dependency of the graph.
     * 
     * @param root The root dependency of the graph, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setRoot( Dependency root )
    {
        this.root = root;
        return this;
    }

    /**
     * Gets the direct dependencies.
     * 
     * @return The direct dependencies, never {@code null}.
     */
    public List<Dependency> getDependencies()
    {
        return dependencies;
    }

    /**
     * Sets the direct dependencies. If both a root dependency and direct dependencies are given in the request, the
     * direct dependencies from the request will be merged with the direct dependencies from the root dependency's
     * artifact descriptor, giving higher priority to the dependencies from the request.
     * 
     * @param dependencies The direct dependencies, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setDependencies( List<Dependency> dependencies )
    {
        if ( dependencies == null )
        {
            this.dependencies = Collections.emptyList();
        }
        else
        {
            this.dependencies = dependencies;
        }
        return this;
    }

    /**
     * Adds the specified direct dependency.
     * 
     * @param dependency The dependency to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest addDependency( Dependency dependency )
    {
        if ( dependency != null )
        {
            if ( this.dependencies.isEmpty() )
            {
                this.dependencies = new ArrayList<Dependency>();
            }
            this.dependencies.add( dependency );
        }
        return this;
    }

    /**
     * Gets the dependency management to apply to transitive dependencies.
     * 
     * @return The dependency management to apply to transitive dependencies, never {@code null}.
     */
    public List<Dependency> getManagedDependencies()
    {
        return managedDependencies;
    }

    /**
     * Sets the dependency management to apply to transitive dependencies. To clarify, this management does not apply to
     * the direct dependencies of the root node.
     * 
     * @param managedDependencies The dependency management, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setManagedDependencies( List<Dependency> managedDependencies )
    {
        if ( managedDependencies == null )
        {
            this.managedDependencies = Collections.emptyList();
        }
        else
        {
            this.managedDependencies = managedDependencies;
        }
        return this;
    }

    /**
     * Adds the specified managed dependency.
     * 
     * @param managedDependency The managed dependency to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest addManagedDependency( Dependency managedDependency )
    {
        if ( managedDependency != null )
        {
            if ( this.managedDependencies.isEmpty() )
            {
                this.managedDependencies = new ArrayList<Dependency>();
            }
            this.managedDependencies.add( managedDependency );
        }
        return this;
    }

    /**
     * Gets the repositories to use for the collection.
     * 
     * @return The repositories to use for the collection, never {@code null}.
     */
    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the repositories to use for the collection.
     * 
     * @param repositories The repositories to use for the collection, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setRepositories( List<RemoteRepository> repositories )
    {
        if ( repositories == null )
        {
            this.repositories = Collections.emptyList();
        }
        else
        {
            this.repositories = repositories;
        }
        return this;
    }

    /**
     * Adds the specified repository for collection.
     * 
     * @param repository The repository to collect dependency information from, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest addRepository( RemoteRepository repository )
    {
        if ( repository != null )
        {
            if ( this.repositories.isEmpty() )
            {
                this.repositories = new ArrayList<RemoteRepository>();
            }
            this.repositories.add( repository );
        }
        return this;
    }

    /**
     * Gets the context in which this request is made.
     * 
     * @return The context, never {@code null}.
     */
    public String getRequestContext()
    {
        return context;
    }

    /**
     * Sets the context in which this request is made.
     * 
     * @param context The context, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setRequestContext( String context )
    {
        this.context = ( context != null ) ? context : "";
        return this;
    }

    /**
     * Gets the trace information that describes the higher level request/operation in which this request is issued.
     * 
     * @return The trace information about the higher level operation or {@code null} if none.
     */
    public RequestTrace getTrace()
    {
        return trace;
    }

    /**
     * Sets the trace information that describes the higher level request/operation in which this request is issued.
     * 
     * @param trace The trace information about the higher level operation, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public CollectRequest setTrace( RequestTrace trace )
    {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString()
    {
        return getRoot() + " -> " + getDependencies() + " < " + getRepositories();
    }

}
