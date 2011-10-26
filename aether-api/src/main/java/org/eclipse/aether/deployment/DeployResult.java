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
package org.eclipse.aether.deployment;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * The result of deploying artifacts and their accompanying metadata into the a remote repository.
 * @see RepositorySystem#deploy(RepositorySystemSession, DeployRequest)
 */
public final class DeployResult
{

    private final DeployRequest request;

    private Collection<Artifact> artifacts = new ArrayList<Artifact>();

    private Collection<Metadata> metadata = new ArrayList<Metadata>();

    /**
     * Creates a new result for the specified request.
     * 
     * @param request The deployment request, must not be {@code null}.
     */
    public DeployResult( DeployRequest request )
    {
        if ( request == null )
        {
            throw new IllegalArgumentException( "deploy request has not been specified" );
        }
        this.request = request;
    }

    /**
     * Gets the deploy request that was made.
     * 
     * @return The deploy request, never {@code null}.
     */
    public DeployRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the artifacts that got deployed.
     * 
     * @return The deployed artifacts, never {@code null}.
     */
    public Collection<Artifact> getArtifacts()
    {
        return artifacts;
    }

    /**
     * Sets the artifacts that got deployed.
     * 
     * @param artifacts The deployed artifacts, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DeployResult setArtifacts( Collection<Artifact> artifacts )
    {
        if ( artifacts == null )
        {
            this.artifacts = new ArrayList<Artifact>();
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
     * @param artifact The deployed artifact to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DeployResult addArtifact( Artifact artifact )
    {
        if ( artifact != null )
        {
            artifacts.add( artifact );
        }
        return this;
    }

    /**
     * Gets the metadata that got deployed. Note that due to automatically generated metadata, there might have been
     * more metadata deployed than originally specified in the deploy request.
     * 
     * @return The deployed metadata, never {@code null}.
     */
    public Collection<Metadata> getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata that got deployed.
     * 
     * @param metadata The deployed metadata, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DeployResult setMetadata( Collection<Metadata> metadata )
    {
        if ( metadata == null )
        {
            this.metadata = new ArrayList<Metadata>();
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
     * @param metadata The deployed metadata to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public DeployResult addMetadata( Metadata metadata )
    {
        if ( metadata != null )
        {
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
