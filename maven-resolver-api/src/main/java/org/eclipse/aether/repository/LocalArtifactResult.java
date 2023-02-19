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

import java.io.File;

import static java.util.Objects.requireNonNull;

/**
 * A result from the local repository about the existence of an artifact.
 *
 * @see LocalRepositoryManager#find(org.eclipse.aether.RepositorySystemSession, LocalArtifactRequest)
 */
public final class LocalArtifactResult {

    private final LocalArtifactRequest request;

    private File file;

    private boolean available;

    private RemoteRepository repository;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The local artifact request, must not be {@code null}.
     */
    public LocalArtifactResult(LocalArtifactRequest request) {
        this.request = requireNonNull(request, "local artifact request cannot be null");
    }

    /**
     * Gets the request corresponding to this result.
     *
     * @return The corresponding request, never {@code null}.
     */
    public LocalArtifactRequest getRequest() {
        return request;
    }

    /**
     * Gets the file to the requested artifact. Note that this file must not be used unless {@link #isAvailable()}
     * returns {@code true}. An artifact file can be found but considered unavailable if the artifact was cached from a
     * remote repository that is not part of the list of remote repositories used for the query.
     *
     * @return The file to the requested artifact or {@code null} if the artifact does not exist locally.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the file to requested artifact.
     *
     * @param file The artifact file, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public LocalArtifactResult setFile(File file) {
        this.file = file;
        return this;
    }

    /**
     * Indicates whether the requested artifact is available for use. As a minimum, the file needs to be physically
     * existent in the local repository to be available. Additionally, a local repository manager can consider the list
     * of supplied remote repositories to determine whether the artifact is logically available and mark an artifact
     * unavailable (despite its physical existence) if it is not known to be hosted by any of the provided repositories.
     *
     * @return {@code true} if the artifact is available, {@code false} otherwise.
     * @see LocalArtifactRequest#getRepositories()
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Sets whether the artifact is available.
     *
     * @param available {@code true} if the artifact is available, {@code false} otherwise.
     * @return This result for chaining, never {@code null}.
     */
    public LocalArtifactResult setAvailable(boolean available) {
        this.available = available;
        return this;
    }

    /**
     * Gets the (first) remote repository from which the artifact was cached (if any).
     *
     * @return The remote repository from which the artifact was originally retrieved or {@code null} if unknown or if
     *         the artifact has been locally installed.
     * @see LocalArtifactRequest#getRepositories()
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Sets the (first) remote repository from which the artifact was cached.
     *
     * @param repository The remote repository from which the artifact was originally retrieved, may be {@code null} if
     *            unknown or if the artifact has been locally installed.
     * @return This result for chaining, never {@code null}.
     */
    public LocalArtifactResult setRepository(RemoteRepository repository) {
        this.repository = repository;
        return this;
    }

    @Override
    public String toString() {
        return getFile() + " (" + (isAvailable() ? "available" : "unavailable") + ")";
    }
}
