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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when an artifact could not be uploaded/downloaded to/from a particular remote repository.
 */
public class ArtifactTransferException extends RepositoryException {

    private final transient Artifact artifact;

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
     * Creates a new exception with the specified artifact, repository and detail message.
     *
     * @param artifact The untransferable artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public ArtifactTransferException(Artifact artifact, RemoteRepository repository, String message) {
        this(artifact, repository, message, false);
    }

    /**
     * Creates a new exception with the specified artifact, repository and detail message.
     *
     * @param artifact The untransferable artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param fromCache {@code true} if the exception was played back from the error cache, {@code false} if the
     *            exception actually just occurred.
     */
    public ArtifactTransferException(
            Artifact artifact, RemoteRepository repository, String message, boolean fromCache) {
        super(message);
        this.artifact = artifact;
        this.repository = repository;
        this.fromCache = fromCache;
    }

    /**
     * Creates a new exception with the specified artifact, repository and cause.
     *
     * @param artifact The untransferable artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ArtifactTransferException(Artifact artifact, RemoteRepository repository, Throwable cause) {
        this(
                artifact,
                repository,
                "Could not transfer artifact " + artifact + getString(" from/to ", repository)
                        + getMessage(": ", cause),
                cause);
    }

    /**
     * Creates a new exception with the specified artifact, repository, detail message and cause.
     *
     * @param artifact The untransferable artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ArtifactTransferException(Artifact artifact, RemoteRepository repository, String message, Throwable cause) {
        super(message, cause);
        this.artifact = artifact;
        this.repository = repository;
        this.fromCache = false;
    }

    /**
     * Gets the artifact that could not be transferred.
     *
     * @return The troublesome artifact or {@code null} if unknown.
     */
    public Artifact getArtifact() {
        return artifact;
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
