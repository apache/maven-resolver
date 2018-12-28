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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * The result from reading an artifact descriptor.
 * 
 * @see RepositorySystem#readArtifactDescriptor(RepositorySystemSession, ArtifactDescriptorRequest)
 */
public final class ArtifactDescriptorResult
{

    private final ArtifactDescriptorRequest request;

    private List<Exception> exceptions;

    private List<Artifact> relocations;

    private Collection<Artifact> aliases;

    private Artifact artifact;

    private ArtifactRepository repository;

    private List<Dependency> dependencies;

    private List<Dependency> managedDependencies;

    private List<RemoteRepository> repositories;

    private Map<String, Object> properties;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The descriptor request, must not be {@code null}.
     */
    public ArtifactDescriptorResult( ArtifactDescriptorRequest request )
    {
        this.request = requireNonNull( request, "artifact descriptor request cannot be null" );
        artifact = request.getArtifact();
        exceptions = Collections.emptyList();
        relocations = Collections.emptyList();
        aliases = Collections.emptyList();
        dependencies = managedDependencies = Collections.emptyList();
        repositories = Collections.emptyList();
        properties = Collections.emptyMap();
    }

    /**
     * Gets the descriptor request that was made.
     * 
     * @return The descriptor request, never {@code null}.
     */
    public ArtifactDescriptorRequest getRequest()
    {
        return request;
    }

    /**
     * Gets the exceptions that occurred while reading the artifact descriptor.
     * 
     * @return The exceptions that occurred, never {@code null}.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    /**
     * Sets the exceptions that occurred while reading the artifact descriptor.
     * 
     * @param exceptions The exceptions that occurred, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setExceptions( List<Exception> exceptions )
    {
        if ( exceptions == null )
        {
            this.exceptions = Collections.emptyList();
        }
        else
        {
            this.exceptions = exceptions;
        }
        return this;
    }

    /**
     * Records the specified exception while reading the artifact descriptor.
     * 
     * @param exception The exception to record, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addException( Exception exception )
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
     * Gets the relocations that were processed to read the artifact descriptor. The returned list denotes the hops that
     * lead to the final artifact coordinates as given by {@link #getArtifact()}.
     * 
     * @return The relocations that were processed, never {@code null}.
     */
    public List<Artifact> getRelocations()
    {
        return relocations;
    }

    /**
     * Sets the relocations that were processed to read the artifact descriptor.
     * 
     * @param relocations The relocations that were processed, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setRelocations( List<Artifact> relocations )
    {
        if ( relocations == null )
        {
            this.relocations = Collections.emptyList();
        }
        else
        {
            this.relocations = relocations;
        }
        return this;
    }

    /**
     * Records the specified relocation hop while locating the artifact descriptor.
     * 
     * @param artifact The artifact that got relocated, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addRelocation( Artifact artifact )
    {
        if ( artifact != null )
        {
            if ( relocations.isEmpty() )
            {
                relocations = new ArrayList<>();
            }
            relocations.add( artifact );
        }
        return this;
    }

    /**
     * Gets the known aliases for this artifact. An alias denotes a different artifact with (almost) the same contents
     * and can be used to mark a patched rebuild of some other artifact as such, thereby allowing conflict resolution to
     * consider the patched and the original artifact as a conflict.
     * 
     * @return The aliases of the artifact, never {@code null}.
     */
    public Collection<Artifact> getAliases()
    {
        return aliases;
    }

    /**
     * Sets the aliases of the artifact.
     * 
     * @param aliases The aliases of the artifact, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setAliases( Collection<Artifact> aliases )
    {
        if ( aliases == null )
        {
            this.aliases = Collections.emptyList();
        }
        else
        {
            this.aliases = aliases;
        }
        return this;
    }

    /**
     * Records the specified alias.
     * 
     * @param alias The alias for the artifact, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addAlias( Artifact alias )
    {
        if ( alias != null )
        {
            if ( aliases.isEmpty() )
            {
                aliases = new ArrayList<>();
            }
            aliases.add( alias );
        }
        return this;
    }

    /**
     * Gets the artifact whose descriptor was read. This can be a different artifact than originally requested in case
     * relocations were encountered.
     * 
     * @return The artifact after following any relocations, never {@code null}.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact whose descriptor was read.
     * 
     * @param artifact The artifact whose descriptor was read, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the repository from which the descriptor was eventually resolved.
     * 
     * @return The repository from which the descriptor was resolved or {@code null} if unknown.
     */
    public ArtifactRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the repository from which the descriptor was resolved.
     * 
     * @param repository The repository from which the descriptor was resolved, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setRepository( ArtifactRepository repository )
    {
        this.repository = repository;
        return this;
    }

    /**
     * Gets the list of direct dependencies of the artifact.
     * 
     * @return The list of direct dependencies, never {@code null}
     */
    public List<Dependency> getDependencies()
    {
        return dependencies;
    }

    /**
     * Sets the list of direct dependencies of the artifact.
     * 
     * @param dependencies The list of direct dependencies, may be {@code null}
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setDependencies( List<Dependency> dependencies )
    {
        if ( dependencies == null )
        {
            this.dependencies = Collections.emptyList();
        }
        else
        {
            this.dependencies = dependencies;
        }
        return this;
    }

    /**
     * Adds the specified direct dependency.
     * 
     * @param dependency The direct dependency to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addDependency( Dependency dependency )
    {
        if ( dependency != null )
        {
            if ( dependencies.isEmpty() )
            {
                dependencies = new ArrayList<>();
            }
            dependencies.add( dependency );
        }
        return this;
    }

    /**
     * Gets the dependency management information.
     * 
     * @return The dependency management information.
     */
    public List<Dependency> getManagedDependencies()
    {
        return managedDependencies;
    }

    /**
     * Sets the dependency management information.
     * 
     * @param dependencies The dependency management information, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setManagedDependencies( List<Dependency> dependencies )
    {
        if ( dependencies == null )
        {
            this.managedDependencies = Collections.emptyList();
        }
        else
        {
            this.managedDependencies = dependencies;
        }
        return this;
    }

    /**
     * Adds the specified managed dependency.
     * 
     * @param dependency The managed dependency to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addManagedDependency( Dependency dependency )
    {
        if ( dependency != null )
        {
            if ( managedDependencies.isEmpty() )
            {
                managedDependencies = new ArrayList<>();
            }
            managedDependencies.add( dependency );
        }
        return this;
    }

    /**
     * Gets the remote repositories listed in the artifact descriptor.
     * 
     * @return The remote repositories listed in the artifact descriptor, never {@code null}.
     */
    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the remote repositories listed in the artifact descriptor.
     * 
     * @param repositories The remote repositories listed in the artifact descriptor, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setRepositories( List<RemoteRepository> repositories )
    {
        if ( repositories == null )
        {
            this.repositories = Collections.emptyList();
        }
        else
        {
            this.repositories = repositories;
        }
        return this;
    }

    /**
     * Adds the specified remote repository.
     * 
     * @param repository The remote repository to add, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult addRepository( RemoteRepository repository )
    {
        if ( repository != null )
        {
            if ( repositories.isEmpty() )
            {
                repositories = new ArrayList<>();
            }
            repositories.add( repository );
        }
        return this;
    }

    /**
     * Gets any additional information about the artifact in form of key-value pairs. <em>Note:</em> Regardless of their
     * actual type, all property values must be treated as being read-only.
     * 
     * @return The additional information about the artifact, never {@code null}.
     */
    public Map<String, Object> getProperties()
    {
        return properties;
    }

    /**
     * Sets any additional information about the artifact in form of key-value pairs.
     * 
     * @param properties The additional information about the artifact, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public ArtifactDescriptorResult setProperties( Map<String, Object> properties )
    {
        if ( properties == null )
        {
            this.properties = Collections.emptyMap();
        }
        else
        {
            this.properties = properties;
        }
        return this;
    }

    @Override
    public String toString()
    {
        return getArtifact() + " -> " + getDependencies();
    }

}
