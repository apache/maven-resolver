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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when metadata could not be uploaded/downloaded to/from a particular remote repository.
 */
public class MetadataTransferException extends RepositoryException {

    private final transient Metadata metadata;

    private final transient RemoteRepository repository;

    private final boolean fromCache;

    static String getString(String prefix, RemoteRepository repository) {
        if (repository == null) {
            return "";
        } else {
            return prefix + repository.getId() + " (" + repository.getUrl() + ")";
        }
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     *
     * @param metadata The untransferable metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public MetadataTransferException(Metadata metadata, RemoteRepository repository, String message) {
        this(metadata, repository, message, false);
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     *
     * @param metadata The untransferable metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param fromCache {@code true} if the exception was played back from the error cache, {@code false} if the
     *            exception actually just occurred.
     */
    public MetadataTransferException(
            Metadata metadata, RemoteRepository repository, String message, boolean fromCache) {
        super(message);
        this.metadata = metadata;
        this.repository = repository;
        this.fromCache = fromCache;
    }

    /**
     * Creates a new exception with the specified metadata, repository and cause.
     *
     * @param metadata The untransferable metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public MetadataTransferException(Metadata metadata, RemoteRepository repository, Throwable cause) {
        this(
                metadata,
                repository,
                "Could not transfer metadata " + metadata + getString(" from/to ", repository)
                        + getMessage(": ", cause),
                cause);
    }

    /**
     * Creates a new exception with the specified metadata, repository, detail message and cause.
     *
     * @param metadata The untransferable metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public MetadataTransferException(Metadata metadata, RemoteRepository repository, String message, Throwable cause) {
        super(message, cause);
        this.metadata = metadata;
        this.repository = repository;
        this.fromCache = false;
    }

    /**
     * Gets the metadata that could not be transferred.
     *
     * @return The troublesome metadata or {@code null} if unknown.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Gets the remote repository involved in the transfer.
     *
     * @return The involved remote repository or {@code null} if unknown.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Indicates whether this exception actually just occurred or was played back from the error cache.
     *
     * @return {@code true} if the exception was played back from the error cache, {@code false} if the exception
     *         actually occurred just now.
     */
    public boolean isFromCache() {
        return fromCache;
    }
}
