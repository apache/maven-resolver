package org.eclipse.aether.resolution;

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
import java.util.Collections;
import java.util.List;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.transfer.ArtifactNotFoundException;

/**
 * The result of an artifact resolution request.
 * 
 * @see RepositorySystem#resolveArtifacts(RepositorySystemSession, java.util.Collection)
 * @see Artifact#getFile()
 */
public final class ArtifactResult
{

    private final ArtifactRequest request;

    private List<Exception> exceptions;

    private Artifact artifact;

    private ArtifactRepository repository;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The resolution request, must not be {@code null}.
     */
    public ArtifactResult( ArtifactRequest request )
    {
        this.request = requireNonNull( request, "artifact request cannot be null" );
        exceptions = Collections.emptyList();
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
            if ( exceptions.isEmpty() )
            {
                exceptions = new ArrayList<>();
            }
            exceptions.add( exception );
        }
        return this;
    }

    /**
     * Gets the repository from which the artifact was eventually resolved. Note that successive resolutions of the same
     * artifact might yield different results if the employed local repository does not track the origin of an artifact.
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
