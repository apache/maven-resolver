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
package org.eclipse.aether.resolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to resolve an artifact.
 * @see RepositorySystem#resolveArtifacts(RepositorySystemSession, java.util.Collection)
 * @see Artifact#getFile()
 */
public final class ArtifactRequest
{

    private Artifact artifact;

    private DependencyNode node;

    private List<RemoteRepository> repositories = Collections.emptyList();

    private String context = "";

    private RequestTrace trace;

    /**
     * Creates an unitialized request.
     */
    public ArtifactRequest()
    {
        // enables default constructor
    }

    /**
     * Creates a request with the specified properties.
     * 
     * @param artifact The artifact to resolve, may be {@code null}.
     * @param repositories The repositories to resolve the artifact from, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public ArtifactRequest( Artifact artifact, List<RemoteRepository> repositories, String context )
    {
        setArtifact( artifact );
        setRepositories( repositories );
        setRequestContext( context );
    }

    /**
     * Creates a request from the specified dependency node.
     * 
     * @param node The dependency node to resolve, may be {@code null}.
     */
    public ArtifactRequest( DependencyNode node )
    {
        setDependencyNode( node );
        setRepositories( node.getRepositories() );
        setRequestContext( node.getRequestContext() );
    }

    /**
     * Gets the artifact to resolve.
     * 
     * @return The artifact to resolve or {@code null}.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact to resolve.
     * 
     * @param artifact The artifact to resolve, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactRequest setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the dependency node (if any) for which to resolve the artifact.
     * 
     * @return The dependency node to resolve or {@code null} if unknown.
     */
    public DependencyNode getDependencyNode()
    {
        return node;
    }

    /**
     * Sets the dependency node to resolve.
     * 
     * @param node The dependency node to resolve, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactRequest setDependencyNode( DependencyNode node )
    {
        this.node = node;
        if ( node != null )
        {
            setArtifact( node.getDependency().getArtifact() );
        }
        return this;
    }

    /**
     * Gets the repositories to resolve the artifact from.
     * 
     * @return The repositories, never {@code null}.
     */
    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the repositories to resolve the artifact from.
     * 
     * @param repositories The repositories, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactRequest setRepositories( List<RemoteRepository> repositories )
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
     * Adds the specified repository for the resolution.
     * 
     * @param repository The repository to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactRequest addRepository( RemoteRepository repository )
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
    public ArtifactRequest setRequestContext( String context )
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
    public ArtifactRequest setTrace( RequestTrace trace )
    {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString()
    {
        return getArtifact() + " < " + getRepositories();
    }

}
