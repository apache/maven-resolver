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
import org.eclipse.aether.repository.ArtifactRepository;

/**
 * The result of a version resolution request.
 * 
 * @see RepositorySystem#resolveVersion(RepositorySystemSession, VersionRequest)
 */
public final class VersionResult
{

    private final VersionRequest request;

    private List<Exception> exceptions;

    private String version;

    private ArtifactRepository repository;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The resolution request, must not be {@code null}.
     */
    public VersionResult( VersionRequest request )
    {
        this.request = requireNonNull( request, "version request cannot be null" );
        exceptions = Collections.emptyList();
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
            if ( exceptions.isEmpty() )
            {
                exceptions = new ArrayList<>();
            }
            exceptions.add( exception );
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
