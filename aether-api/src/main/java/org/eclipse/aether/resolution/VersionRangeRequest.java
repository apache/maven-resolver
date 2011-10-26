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
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to resolve a version range.
 * @see RepositorySystem#resolveVersionRange(RepositorySystemSession, VersionRangeRequest)
 */
public final class VersionRangeRequest
{

    private Artifact artifact;

    private List<RemoteRepository> repositories = Collections.emptyList();

    private String context = "";

    private RequestTrace trace;

    /**
     * Creates an uninitialized request.
     */
    public VersionRangeRequest()
    {
        // enables default constructor
    }

    /**
     * Creates a request with the specified properties.
     * 
     * @param artifact The artifact whose version range should be resolved, may be {@code null}.
     * @param repositories The repositories to resolve the version from, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public VersionRangeRequest( Artifact artifact, List<RemoteRepository> repositories, String context )
    {
        setArtifact( artifact );
        setRepositories( repositories );
        setRequestContext( context );
    }

    /**
     * Gets the artifact whose version range shall be resolved.
     * 
     * @return The artifact or {@code null} if not set.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact whose version range shall be resolved.
     * 
     * @param artifact The artifact, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the repositories to resolve the version range from.
     * 
     * @return The repositories, never {@code null}.
     */
    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the repositories to resolve the version range from.
     * 
     * @param repositories The repositories, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public VersionRangeRequest setRepositories( List<RemoteRepository> repositories )
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
    public VersionRangeRequest addRepository( RemoteRepository repository )
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
    public VersionRangeRequest setRequestContext( String context )
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
    public VersionRangeRequest setTrace( RequestTrace trace )
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
