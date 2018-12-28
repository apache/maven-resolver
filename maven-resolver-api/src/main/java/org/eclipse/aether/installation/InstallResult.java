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
import static java.util.Objects.requireNonNull;

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
        this.request = requireNonNull( request, "install request cannot be null" );
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
                artifacts = new ArrayList<>();
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
                this.metadata = new ArrayList<>();
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
