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
import org.eclipse.aether.metadata.Metadata;

/**
 * A request to register metadata within the local repository.
 * 
 * @see LocalRepositoryManager#add(RepositorySystemSession, LocalMetadataRegistration)
 */
public final class LocalMetadataRegistration
{

    private Metadata metadata;

    private RemoteRepository repository;

    private Collection<String> contexts = Collections.emptyList();

    /**
     * Creates an uninitialized registration.
     */
    public LocalMetadataRegistration()
    {
        // enables default constructor
    }

    /**
     * Creates a registration request for the specified metadata accompanying a locally installed artifact.
     * 
     * @param metadata The metadata to register, may be {@code null}.
     */
    public LocalMetadataRegistration( Metadata metadata )
    {
        setMetadata( metadata );
    }

    /**
     * Creates a registration request for the specified metadata.
     * 
     * @param metadata The metadata to register, may be {@code null}.
     * @param repository The remote repository from which the metadata was resolved or {@code null} if the metadata
     *            accompanies a locally installed artifact.
     * @param contexts The resolution contexts, may be {@code null}.
     */
    public LocalMetadataRegistration( Metadata metadata, RemoteRepository repository, Collection<String> contexts )
    {
        setMetadata( metadata );
        setRepository( repository );
        setContexts( contexts );
    }

    /**
     * Gets the metadata to register.
     * 
     * @return The metadata or {@code null} if not set.
     */
    public Metadata getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata to register.
     * 
     * @param metadata The metadata, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setMetadata( Metadata metadata )
    {
        this.metadata = metadata;
        return this;
    }

    /**
     * Gets the remote repository from which the metadata was resolved.
     * 
     * @return The remote repository or {@code null} if the metadata was locally installed.
     */
    public RemoteRepository getRepository()
    {
        return repository;
    }

    /**
     * Sets the remote repository from which the metadata was resolved.
     * 
     * @param repository The remote repository or {@code null} if the metadata accompanies a locally installed artifact.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setRepository( RemoteRepository repository )
    {
        this.repository = repository;
        return this;
    }

    /**
     * Gets the resolution contexts in which the metadata is available.
     * 
     * @return The resolution contexts in which the metadata is available, never {@code null}.
     */
    public Collection<String> getContexts()
    {
        return contexts;
    }

    /**
     * Sets the resolution contexts in which the metadata is available.
     * 
     * @param contexts The resolution contexts, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setContexts( Collection<String> contexts )
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
