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

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when metadata was not found in a particular repository.
 */
public class MetadataNotFoundException extends MetadataTransferException {

    /**
     * Creates a new exception with the specified metadata and local repository.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved local repository, may be {@code null}.
     */
    public MetadataNotFoundException(Metadata metadata, LocalRepository repository) {
        super(metadata, null, getMessage(metadata, repository));
    }

    /**
     * Creates a new exception with the specified metadata and repository.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     */
    public MetadataNotFoundException(Metadata metadata, RemoteRepository repository) {
        super(metadata, repository, getMessage(metadata, repository));
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public MetadataNotFoundException(Metadata metadata, RemoteRepository repository, String message) {
        super(metadata, repository, message);
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param fromCache {@code true} if the exception was played back from the error cache, {@code false} if the
     *            exception actually just occurred.
     */
    public MetadataNotFoundException(
            Metadata metadata, RemoteRepository repository, String message, boolean fromCache) {
        super(metadata, repository, message, fromCache);
    }

    /**
     * Creates a new exception with the specified metadata, repository, detail message and cause.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public MetadataNotFoundException(Metadata metadata, RemoteRepository repository, String message, Throwable cause) {
        super(metadata, repository, message, cause);
    }

    /**
     * Creates a new exception with the specified metadata, repository and root cause.
     *
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     * @since 2.0.26
     */
    public MetadataNotFoundException(Metadata metadata, RemoteRepository repository, Throwable cause) {
        super(metadata, repository, getMessage(metadata, repository), cause);
    }

    private static String getMessage(Metadata metadata, ArtifactRepository repository) {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append("Could not find metadata ").append(metadata);
        buffer.append(getString(" in ", repository));
        return buffer.toString();
    }
}
