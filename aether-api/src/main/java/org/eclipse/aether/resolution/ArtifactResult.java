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
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.transfer.ArtifactNotFoundException;

/**
 * The result of an artifact resolution request.
 * @see RepositorySystem#resolveArtifacts(RepositorySystemSession, java.util.Collection)
 * @see Artifact#getFile()
 */
public final class ArtifactResult
{

    private final ArtifactRequest request;

    private final List<Exception> exceptions;

    private Artifact artifact;

    private ArtifactRepository repository;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The resolution request, must not be {@code null}.
     */
    public ArtifactResult( ArtifactRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "resolution request has not been specified" );
        }
        this.request = request;
        this.exceptions = new ArrayList<Exception>( 2 );
    }

    /**
     * Gets the resolution request that was made.
     * 
     * @return The resolution request, never {@code null}.
     */
    public ArtifactRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the resolved artifact (if any). Use {@link #getExceptions()} to query the errors that occurred while trying
     * to resolve the artifact.
     * 
     * @return The resolved artifact or {@code null} if the resolution failed.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the resolved artifact.
     * 
     * @param artifact The resolved artifact, may be {@code null} if the resolution failed.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactResult setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the exceptions that occurred while resolving the artifact. Note that this list can be non-empty even if the
     * artifact was successfully resolved, e.g. when one of the contacted remote repositories didn't contain the
     * artifact but a later repository eventually contained it.
     * 
     * @return The exceptions that occurred, never {@code null}.
     * @see #isResolved()
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Records the specified exception while resolving the artifact.
     * 
     * @param exception The exception to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactResult addException( Exception exception )
    {
        if ( exception != null )
        {
            this.exceptions.add( exception );
        }
        return this;
    }

    /**
     * Gets the repository from which the artifact was eventually resolved.
     * 
     * @return The repository from which the artifact was resolved or {@code null} if unknown.
     */
    public ArtifactRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the repository from which the artifact was resolved.
     * 
     * @param repository The repository from which the artifact was resolved, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactResult setRepository( ArtifactRepository repository )
    {
        this.repository = repository;
        return this;
    }

    /**
     * Indicates whether the requested artifact was resolved. Note that the artifact might have been successfully
     * resolved despite {@link #getExceptions()} indicating transfer errors while trying to fetch the artifact from some
     * of the specified remote repositories.
     * 
     * @return {@code true} if the artifact was resolved, {@code false} otherwise.
     * @see Artifact#getFile()
     */
    public boolean isResolved()
    {
        return getArtifact() != null && getArtifact().getFile() != null;
    }

    /**
     * Indicates whether the requested artifact is not present in any of the specified repositories.
     * 
     * @return {@code true} if the artifact is not present in any repository, {@code false} otherwise.
     */
    public boolean isMissing()
    {
        for ( Exception e : getExceptions() )
        {
            if ( !( e instanceof ArtifactNotFoundException ) )
            {
                return false;
            }
        }
        return !isResolved();
    }

    @Override
    public String toString()
    {
        return getArtifact() + " < " + getRepository();
    }

}
