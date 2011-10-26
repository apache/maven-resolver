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
import org.eclipse.aether.repository.ArtifactRepository;

/**
 * The result of a version resolution request.
 * @see RepositorySystem#resolveVersion(RepositorySystemSession, VersionRequest)
 */
public final class VersionResult
{

    private final VersionRequest request;

    private final List<Exception> exceptions;

    private String version;

    private ArtifactRepository repository;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The resolution request, must not be {@code null}.
     */
    public VersionResult( VersionRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "version request has not been specified" );
        }
        this.request = request;
        this.exceptions = new ArrayList<Exception>( 4 );
    }

    /**
     * Gets the resolution request that was made.
     * 
     * @return The resolution request, never {@code null}.
     */
    public VersionRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the exceptions that occurred while resolving the version.
     * 
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Records the specified exception while resolving the version.
     * 
     * @param exception The exception to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionResult addException( Exception exception )
    {
        if ( exception != null )
        {
            this.exceptions.add( exception );
        }
        return this;
    }

    /**
     * Gets the resolved version.
     * 
     * @return The resolved version or {@code null} if the resolution failed.
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * Sets the resolved version.
     * 
     * @param version The resolved version, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionResult setVersion( String version )
    {
        this.version = version;
        return this;
    }

    /**
     * Gets the repository from which the version was eventually resolved.
     * 
     * @return The repository from which the version was resolved or {@code null} if unknown.
     */
    public ArtifactRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the repository from which the version was resolved.
     * 
     * @param repository The repository from which the version was resolved, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionResult setRepository( ArtifactRepository repository )
    {
        this.repository = repository;
        return this;
    }

    @Override
    public String toString()
    {
        return getVersion() + " @ " + getRepository();
    }

}
