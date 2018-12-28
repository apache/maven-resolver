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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * The result of a version range resolution request.
 * 
 * @see RepositorySystem#resolveVersionRange(RepositorySystemSession, VersionRangeRequest)
 */
public final class VersionRangeResult
{

    private final VersionRangeRequest request;

    private List<Exception> exceptions;

    private List<Version> versions;

    private Map<Version, ArtifactRepository> repositories;

    private VersionConstraint versionConstraint;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The resolution request, must not be {@code null}.
     */
    public VersionRangeResult( VersionRangeRequest request )
    {
        this.request = requireNonNull( request, "version range request cannot be null" );
        exceptions = Collections.emptyList();
        versions = Collections.emptyList();
        repositories = Collections.emptyMap();
    }

    /**
     * Gets the resolution request that was made.
     * 
     * @return The resolution request, never {@code null}.
     */
    public VersionRangeRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the exceptions that occurred while resolving the version range.
     * 
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Records the specified exception while resolving the version range.
     * 
     * @param exception The exception to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionRangeResult addException( Exception exception )
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
     * Gets the versions (in ascending order) that matched the requested range.
     * 
     * @return The matching versions (if any), never {@code null}.
     */
    public List<Version> getVersions()
    {
        return versions;
    }

    /**
     * Adds the specified version to the result. Note that versions must be added in ascending order.
     * 
     * @param version The version to add, must not be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionRangeResult addVersion( Version version )
    {
        if ( versions.isEmpty() )
        {
            versions = new ArrayList<>();
        }
        versions.add( version );
        return this;
    }

    /**
     * Sets the versions (in ascending order) matching the requested range.
     * 
     * @param versions The matching versions, may be empty or {@code null} if none.
     * @return This result for chaining, never {@code null}.
     */
    public VersionRangeResult setVersions( List<Version> versions )
    {
        if ( versions == null )
        {
            this.versions = Collections.emptyList();
        }
        else
        {
            this.versions = versions;
        }
        return this;
    }

    /**
     * Gets the lowest version matching the requested range.
     * 
     * @return The lowest matching version or {@code null} if no versions matched the requested range.
     */
    public Version getLowestVersion()
    {
        if ( versions.isEmpty() )
        {
            return null;
        }
        return versions.get( 0 );
    }

    /**
     * Gets the highest version matching the requested range.
     * 
     * @return The highest matching version or {@code null} if no versions matched the requested range.
     */
    public Version getHighestVersion()
    {
        if ( versions.isEmpty() )
        {
            return null;
        }
        return versions.get( versions.size() - 1 );
    }

    /**
     * Gets the repository from which the specified version was resolved.
     * 
     * @param version The version whose source repository should be retrieved, must not be {@code null}.
     * @return The repository from which the version was resolved or {@code null} if unknown.
     */
    public ArtifactRepository getRepository( Version version )
    {
        return repositories.get( version );
    }

    /**
     * Records the repository from which the specified version was resolved
     * 
     * @param version The version whose source repository is to be recorded, must not be {@code null}.
     * @param repository The repository from which the version was resolved, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionRangeResult setRepository( Version version, ArtifactRepository repository )
    {
        if ( repository != null )
        {
            if ( repositories.isEmpty() )
            {
                repositories = new HashMap<>();
            }
            repositories.put( version, repository );
        }
        return this;
    }

    /**
     * Gets the version constraint that was parsed from the artifact's version string.
     * 
     * @return The parsed version constraint or {@code null}.
     */
    public VersionConstraint getVersionConstraint()
    {
        return versionConstraint;
    }

    /**
     * Sets the version constraint that was parsed from the artifact's version string.
     * 
     * @param versionConstraint The parsed version constraint, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public VersionRangeResult setVersionConstraint( VersionConstraint versionConstraint )
    {
        this.versionConstraint = versionConstraint;
        return this;
    }

    @Override
    public String toString()
    {
        return String.valueOf( repositories );
    }

}
