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

import org.eclipse.aether.metadata.Metadata;

/**
 * A query to the local repository for the existence of metadata.
 *
 * @see LocalRepositoryManager#find(org.eclipse.aether.RepositorySystemSession, LocalMetadataRequest)
 */
public final class LocalMetadataRequest {

    private Metadata metadata;

    private String context = "";

    private RemoteRepository repository = null;

    /**
     * Creates an uninitialized query.
     */
    public LocalMetadataRequest() {
        // enables default constructor
    }

    /**
     * Creates a query with the specified properties.
     *
     * @param metadata The metadata to query for, may be {@code null}.
     * @param repository The source remote repository for the metadata, may be {@code null} for local metadata.
     * @param context The resolution context for the metadata, may be {@code null}.
     */
    public LocalMetadataRequest(Metadata metadata, RemoteRepository repository, String context) {
        setMetadata(metadata);
        setRepository(repository);
        setContext(context);
    }

    /**
     * Gets the metadata to query for.
     *
     * @return The metadata or {@code null} if not set.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata to query for.
     *
     * @param metadata The metadata, may be {@code null}.
     * @return This query for chaining, never {@code null}.
     */
    public LocalMetadataRequest setMetadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Gets the resolution context.
     *
     * @return The resolution context, never {@code null}.
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the resolution context.
     *
     * @param context The resolution context, may be {@code null}.
     * @return This query for chaining, never {@code null}.
     */
    public LocalMetadataRequest setContext(String context) {
        this.context = (context != null) ? context : "";
        return this;
    }

    /**
     * Gets the remote repository to use as source of the metadata.
     *
     * @return The remote repositories, may be {@code null} for local metadata.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Sets the remote repository to use as sources of the metadata.
     *
     * @param repository The remote repository, may be {@code null}.
     * @return This query for chaining, may be {@code null} for local metadata.
     */
    public LocalMetadataRequest setRepository(RemoteRepository repository) {
        this.repository = repository;
        return this;
    }

    @Override
    public String toString() {
        return getMetadata() + " @ " + getRepository();
    }
}
