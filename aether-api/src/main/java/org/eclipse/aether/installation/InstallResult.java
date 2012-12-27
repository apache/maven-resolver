/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.installation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * The result of installing artifacts and their accompanying metadata into the a remote repository.
 * 
 * @see RepositorySystem#install(RepositorySystemSession, InstallRequest)
 */
public final class InstallResult
{

    private final InstallRequest request;

    private Collection<Artifact> artifacts;

    private Collection<Metadata> metadata;

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The installation request, must not be {@code null}.
     */
    public InstallResult( InstallRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "install request has not been specified" );
        }
        this.request = request;
        artifacts = Collections.emptyList();
        metadata = Collections.emptyList();
    }

    /**
     * Gets the install request that was made.
     * 
     * @return The install request, never {@code null}.
     */
    public InstallRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the artifacts that got installed.
     * 
     * @return The installed artifacts, never {@code null}.
     */
    public Collection<Artifact> getArtifacts()
    {
        return artifacts;
    }

    /**
     * Sets the artifacts that got installed.
     * 
     * @param artifacts The installed artifacts, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public InstallResult setArtifacts( Collection<Artifact> artifacts )
    {
        if ( artifacts == null )
        {
            this.artifacts = Collections.emptyList();
        }
        else
        {
            this.artifacts = artifacts;
        }
        return this;
    }

    /**
     * Adds the specified artifacts to the result.
     * 
     * @param artifact The installed artifact to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public InstallResult addArtifact( Artifact artifact )
    {
        if ( artifact != null )
        {
            if ( artifacts.isEmpty() )
            {
                artifacts = new ArrayList<Artifact>();
            }
            artifacts.add( artifact );
        }
        return this;
    }

    /**
     * Gets the metadata that got installed. Note that due to automatically generated metadata, there might have been
     * more metadata installed than originally specified in the install request.
     * 
     * @return The installed metadata, never {@code null}.
     */
    public Collection<Metadata> getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata that got installed.
     * 
     * @param metadata The installed metadata, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public InstallResult setMetadata( Collection<Metadata> metadata )
    {
        if ( metadata == null )
        {
            this.metadata = Collections.emptyList();
        }
        else
        {
            this.metadata = metadata;
        }
        return this;
    }

    /**
     * Adds the specified metadata to this result.
     * 
     * @param metadata The installed metadata to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public InstallResult addMetadata( Metadata metadata )
    {
        if ( metadata != null )
        {
            if ( this.metadata.isEmpty() )
            {
                this.metadata = new ArrayList<Metadata>();
            }
            this.metadata.add( metadata );
        }
        return this;
    }

    @Override
    public String toString()
    {
        return getArtifacts() + ", " + getMetadata();
    }

}
