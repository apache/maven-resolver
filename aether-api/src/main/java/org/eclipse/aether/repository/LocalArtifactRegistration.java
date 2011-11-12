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
package org.eclipse.aether.repository;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;

/**
 * A request to register an artifact within the local repository. Certain local repository implementations can refuse to
 * serve physically present artifacts if those haven't been previously registered to them.
 * 
 * @see LocalRepositoryManager#add(RepositorySystemSession, LocalArtifactRegistration)
 */
public final class LocalArtifactRegistration
{

    private Artifact artifact;

    private RemoteRepository repository;

    private Collection<String> contexts = Collections.emptyList();

    /**
     * Creates an uninitialized registration.
     */
    public LocalArtifactRegistration()
    {
        // enables default constructor
    }

    /**
     * Creates a registration request for the specified (locally installed) artifact.
     * 
     * @param artifact The artifact to register, may be {@code null}.
     */
    public LocalArtifactRegistration( Artifact artifact )
    {
        setArtifact( artifact );
    }

    /**
     * Creates a registration request for the specified artifact.
     * 
     * @param artifact The artifact to register, may be {@code null}.
     * @param repository The remote repository from which the artifact was resolved or {@code null} if the artifact was
     *            locally installed.
     * @param contexts The resolution contexts, may be {@code null}.
     */
    public LocalArtifactRegistration( Artifact artifact, RemoteRepository repository, Collection<String> contexts )
    {
        setArtifact( artifact );
        setRepository( repository );
        setContexts( contexts );
    }

    /**
     * Gets the artifact to register.
     * 
     * @return The artifact or {@code null} if not set.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact to register.
     * 
     * @param artifact The artifact, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalArtifactRegistration setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the remote repository from which the artifact was resolved.
     * 
     * @return The remote repository or {@code null} if the artifact was locally installed.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the remote repository from which the artifact was resolved.
     * 
     * @param repository The remote repository or {@code null} if the artifact was locally installed.
     * @return This request for chaining, never {@code null}.
     */
    public LocalArtifactRegistration setRepository( RemoteRepository repository )
    {
        this.repository = repository;
        return this;
    }

    /**
     * Gets the resolution contexts in which the artifact is available.
     * 
     * @return The resolution contexts in which the artifact is available, never {@code null}.
     */
    public Collection<String> getContexts()
    {
        return contexts;
    }

    /**
     * Sets the resolution contexts in which the artifact is available.
     * 
     * @param contexts The resolution contexts, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalArtifactRegistration setContexts( Collection<String> contexts )
    {
        if ( contexts != null )
        {
            this.contexts = contexts;
        }
        else
        {
            this.contexts = Collections.emptyList();
        }
        return this;
    }

}
