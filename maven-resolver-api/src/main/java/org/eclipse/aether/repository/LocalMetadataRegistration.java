/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.repository;

import java.util.Collection;
import java.util.Collections;
import org.eclipse.aether.metadata.Metadata;

/**
 * A request to register metadata within the local repository.
 *
 * @see LocalRepositoryManager#add(org.eclipse.aether.RepositorySystemSession, LocalMetadataRegistration)
 */
public final class LocalMetadataRegistration {

    private Metadata metadata;

    private RemoteRepository repository;

    private Collection<String> contexts = Collections.emptyList();

    /**
     * Creates an uninitialized registration.
     */
    public LocalMetadataRegistration() {
        // enables default constructor
    }

    /**
     * Creates a registration request for the specified metadata accompanying a locally installed artifact.
     *
     * @param metadata The metadata to register, may be {@code null}.
     */
    public LocalMetadataRegistration(Metadata metadata) {
        setMetadata(metadata);
    }

    /**
     * Creates a registration request for the specified metadata.
     *
     * @param metadata   The metadata to register, may be {@code null}.
     * @param repository The remote repository from which the metadata was resolved or {@code null} if the metadata
     *                   accompanies a locally installed artifact.
     * @param contexts   The resolution contexts, may be {@code null}.
     */
    public LocalMetadataRegistration(Metadata metadata, RemoteRepository repository, Collection<String> contexts) {
        setMetadata(metadata);
        setRepository(repository);
        setContexts(contexts);
    }

    /**
     * Gets the metadata to register.
     *
     * @return The metadata or {@code null} if not set.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata to register.
     *
     * @param metadata The metadata, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setMetadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Gets the remote repository from which the metadata was resolved.
     *
     * @return The remote repository or {@code null} if the metadata was locally installed.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Sets the remote repository from which the metadata was resolved.
     *
     * @param repository The remote repository or {@code null} if the metadata accompanies a locally installed artifact.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setRepository(RemoteRepository repository) {
        this.repository = repository;
        return this;
    }

    /**
     * Gets the resolution contexts in which the metadata is available.
     *
     * @return The resolution contexts in which the metadata is available, never {@code null}.
     */
    public Collection<String> getContexts() {
        return contexts;
    }

    /**
     * Sets the resolution contexts in which the metadata is available.
     *
     * @param contexts The resolution contexts, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public LocalMetadataRegistration setContexts(Collection<String> contexts) {
        if (contexts != null) {
            this.contexts = contexts;
        } else {
            this.contexts = Collections.emptyList();
        }
        return this;
    }
}
