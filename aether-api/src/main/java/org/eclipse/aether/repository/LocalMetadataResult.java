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
package org.eclipse.aether.repository;

import java.io.File;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A result from the local repository about the existence of metadata.
 * 
 * @see LocalRepositoryManager#find(RepositorySystemSession, LocalMetadataRequest)
 */
public final class LocalMetadataResult
{

    private final LocalMetadataRequest request;

    private File file;

    private boolean stale;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The local metadata request, must not be {@code null}.
     */
    public LocalMetadataResult( LocalMetadataRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "local metadata request has not been specified" );
        }
        this.request = request;
    }

    /**
     * Gets the request corresponding to this result.
     * 
     * @return The corresponding request, never {@code null}.
     */
    public LocalMetadataRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the file to the requested metadata if the metadata is available in the local repository.
     * 
     * @return The file to the requested metadata or {@code null}.
     */
    public File getFile()
    {
        return file;
    }

    /**
     * Sets the file to requested metadata.
     * 
     * @param file The metadata file, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public LocalMetadataResult setFile( File file )
    {
        this.file = file;
        return this;
    }

    /**
     * This value indicates whether the metadata is stale and should be updated.
     * 
     * @return {@code true} if the metadata is stale and should be updated, {@code false} otherwise.
     */
    public boolean isStale()
    {
        return stale;
    }

    /**
     * Sets whether the metadata is stale.
     * 
     * @param stale {@code true} if the metadata is stale and should be updated, {@code false} otherwise.
     * @return This result for chaining, never {@code null}.
     */
    public LocalMetadataResult setStale( boolean stale )
    {
        this.stale = stale;
        return this;
    }

    @Override
    public String toString()
    {
        return request.toString() + "(" + getFile() + ")";
    }

}
