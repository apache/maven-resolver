package org.eclipse.aether.deployment;

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
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to deploy artifacts and their accompanying metadata into the a remote repository.
 * 
 * @see RepositorySystem#deploy(RepositorySystemSession, DeployRequest)
 */
public final class DeployRequest
{

    private Collection<Artifact> artifacts = Collections.emptyList();

    private Collection<Metadata> metadata = Collections.emptyList();

    private RemoteRepository repository;

    private RequestTrace trace;

    /**
     * Creates an uninitialized request.
     */
    public DeployRequest()
    {
    }

    /**
     * Gets the artifact to deploy.
     * 
     * @return The artifacts to deploy, never {@code null}.
     */
    public Collection<Artifact> getArtifacts()
    {
        return artifacts;
    }

    /**
     * Sets the artifacts to deploy.
     * 
     * @param artifacts The artifacts to deploy, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DeployRequest setArtifacts( Collection<Artifact> artifacts )
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
     * Adds the specified artifacts for deployment.
     * 
     * @param artifact The artifact to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DeployRequest addArtifact( Artifact artifact )
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
     * Gets the metadata to deploy.
     * 
     * @return The metadata to deploy, never {@code null}.
     */
    public Collection<Metadata> getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata to deploy.
     * 
     * @param metadata The metadata to deploy, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DeployRequest setMetadata( Collection<Metadata> metadata )
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
     * Adds the specified metadata for deployment.
     * 
     * @param metadata The metadata to add, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DeployRequest addMetadata( Metadata metadata )
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
     * Gets the repository to deploy to.
     * 
     * @return The repository to deploy to or {@code null} if not set.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the repository to deploy to.
     * 
     * @param repository The repository to deploy to, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public DeployRequest setRepository( RemoteRepository repository )
    {
        this.repository = repository;
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
    public DeployRequest setTrace( RequestTrace trace )
    {
        this.trace = trace;
        return this;
    }

    @Override
    public String toString()
    {
        return getArtifacts() + ", " + getMetadata() + " > " + getRepository();
    }

}
