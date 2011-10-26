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
package org.eclipse.aether.installation;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * A request to install artifacts and their accompanying metadata into the local repository.
 * @see RepositorySystem#install(RepositorySystemSession, InstallRequest)
 */
public final class InstallRequest
{

    private Collection<Artifact> artifacts = new ArrayList<Artifact>();

    private Collection<Metadata> metadata = new ArrayList<Metadata>();

    private RequestTrace trace;

    /**
     * Gets the artifact to install.
     * 
     * @return The artifacts to install, never {@code null}.
     */
    public Collection<Artifact> getArtifacts()
    {
        return artifacts;
    }

    /**
     * Sets the artifacts to install.
     * 
     * @param artifacts The artifacts to install, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public InstallRequest setArtifacts( Collection<Artifact> artifacts )
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
     * Adds the specified artifacts for installation.
     * 
     * @param artifact The artifact to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public InstallRequest addArtifact( Artifact artifact )
    {
        if ( artifact != null )
        {
            artifacts.add( artifact );
        }
        return this;
    }

    /**
     * Gets the metadata to install.
     * 
     * @return The metadata to install, never {@code null}.
     */
    public Collection<Metadata> getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata to install.
     * 
     * @param metadata The metadata to install.
     * @return This request for chaining, never {@code null}.
     */
    public InstallRequest setMetadata( Collection<Metadata> metadata )
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
     * Adds the specified metadata for installation.
     * 
     * @param metadata The metadata to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public InstallRequest addMetadata( Metadata metadata )
    {
        if ( metadata != null )
        {
            this.metadata.add( metadata );
        }
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
    public InstallRequest setTrace( RequestTrace trace )
    {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString()
    {
        return getArtifacts() + ", " + getMetadata();
    }

}
