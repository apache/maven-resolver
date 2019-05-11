package org.eclipse.aether.installation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * A request to install artifacts and their accompanying metadata into the local repository.
 * 
 * @see RepositorySystem#install(RepositorySystemSession, InstallRequest)
 */
public final class InstallRequest
{

    private Collection<Artifact> artifacts = Collections.emptyList();

    private Collection<Metadata> metadata = Collections.emptyList();

    private RequestTrace trace;

    /**
     * Creates an uninitialized request.
     */
    public InstallRequest()
    {
    }

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
            this.artifacts = Collections.emptyList();
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
            if ( artifacts.isEmpty() )
            {
                artifacts = new ArrayList<>();
            }
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
            this.metadata = Collections.emptyList();
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
            if ( this.metadata.isEmpty() )
            {
                this.metadata = new ArrayList<>();
            }
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
