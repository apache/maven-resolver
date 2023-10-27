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
package org.eclipse.aether.impl;

import java.io.File;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A request to check if an update of an artifact/metadata from a remote repository is needed.
 *
 * @param <T>
 * @param <E>
 * @see UpdateCheckManager
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public final class UpdateCheck<T, E extends RepositoryException> {

    private long localLastUpdated;

    private T item;

    private File file;

    private boolean fileValid = true;

    private String artifactPolicy;

    private String metadataPolicy;

    private RemoteRepository repository;

    private RemoteRepository authoritativeRepository;

    private boolean required;

    private E exception;

    /**
     * Creates an uninitialized update check request.
     */
    public UpdateCheck() {}

    /**
     * Gets the last-modified timestamp of the corresponding item produced by a local installation. If non-zero, a
     * remote update will be surpressed if the local item is up-to-date, even if the remote item has not been cached
     * locally.
     *
     * @return The last-modified timestamp of the corresponding item produced by a local installation or {@code 0} to
     *         ignore any local item.
     */
    public long getLocalLastUpdated() {
        return localLastUpdated;
    }

    /**
     * Sets the last-modified timestamp of the corresponding item produced by a local installation. If non-zero, a
     * remote update will be surpressed if the local item is up-to-date, even if the remote item has not been cached
     * locally.
     *
     * @param localLastUpdated The last-modified timestamp of the corresponding item produced by a local installation or
     *            {@code 0} to ignore any local item.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setLocalLastUpdated(long localLastUpdated) {
        this.localLastUpdated = localLastUpdated;
        return this;
    }

    /**
     * Gets the item of the check.
     *
     * @return The item of the check, never {@code null}.
     */
    public T getItem() {
        return item;
    }

    /**
     * Sets the item of the check.
     *
     * @param item The item of the check, must not be {@code null}.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setItem(T item) {
        this.item = item;
        return this;
    }

    /**
     * Returns the local file of the item.
     *
     * @return The local file of the item.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the local file of the item.
     *
     * @param file The file of the item, never {@code null} .
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setFile(File file) {
        this.file = file;
        return this;
    }

    /**
     * Indicates whether the local file given by {@link #getFile()}, if existent, should be considered valid or not. An
     * invalid file is equivalent to a physically missing file.
     *
     * @return {@code true} if the file should be considered valid if existent, {@code false} if the file should be
     *         treated as if it was missing.
     */
    public boolean isFileValid() {
        return fileValid;
    }

    /**
     * Controls whether the local file given by {@link #getFile()}, if existent, should be considered valid or not. An
     * invalid file is equivalent to a physically missing file.
     *
     * @param fileValid {@code true} if the file should be considered valid if existent, {@code false} if the file
     *            should be treated as if it was missing.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setFileValid(boolean fileValid) {
        this.fileValid = fileValid;
        return this;
    }

    /**
     * Gets the policy to use for the artifact check.
     *
     * @return The policy to use for the artifact check.
     * @see org.eclipse.aether.repository.RepositoryPolicy
     * @since TBD
     */
    public String getArtifactPolicy() {
        return artifactPolicy;
    }

    /**
     * Gets the policy to use for the metadata check.
     *
     * @return The policy to use for the metadata check.
     * @see org.eclipse.aether.repository.RepositoryPolicy
     * @since TBD
     */
    public String getMetadataPolicy() {
        return metadataPolicy;
    }

    /**
     * Sets the artifact policy to use for the check.
     *
     * @param artifactPolicy The policy to use for the artifact check, may be {@code null}.
     * @return This object for chaining.
     * @see org.eclipse.aether.repository.RepositoryPolicy
     * @since TBD
     */
    public UpdateCheck<T, E> setArtifactPolicy(String artifactPolicy) {
        this.artifactPolicy = artifactPolicy;
        return this;
    }

    /**
     * Sets the metadata policy to use for the check.
     *
     * @param metadataPolicy The policy to use for the metadata check, may be {@code null}.
     * @return This object for chaining.
     * @see org.eclipse.aether.repository.RepositoryPolicy
     * @since TBD
     */
    public UpdateCheck<T, E> setMetadataPolicy(String metadataPolicy) {
        this.metadataPolicy = metadataPolicy;
        return this;
    }

    /**
     * Gets the repository from which a potential update/download will performed.
     *
     * @return The repository to use for the check.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Sets the repository from which a potential update/download will performed.
     *
     * @param repository The repository to use for the check, must not be {@code null}.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setRepository(RemoteRepository repository) {
        this.repository = repository;
        return this;
    }

    /**
     * Gets the repository which ultimately hosts the metadata to update. This will be different from the repository
     * given by {@link #getRepository()} in case the latter denotes a repository manager.
     *
     * @return The actual repository hosting the authoritative copy of the metadata to update, never {@code null} for a
     *         metadata update check.
     */
    public RemoteRepository getAuthoritativeRepository() {
        return authoritativeRepository != null ? authoritativeRepository : repository;
    }

    /**
     * Sets the repository which ultimately hosts the metadata to update. This will be different from the repository
     * given by {@link #getRepository()} in case the latter denotes a repository manager.
     *
     * @param authoritativeRepository The actual repository hosting the authoritative copy of the metadata to update,
     *            must not be {@code null} for a metadata update check.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setAuthoritativeRepository(RemoteRepository authoritativeRepository) {
        this.authoritativeRepository = authoritativeRepository;
        return this;
    }

    /**
     * Gets the result of a check, denoting whether the remote repository should be checked for updates.
     *
     * @return The result of a check.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets the result of an update check.
     *
     * @param required The result of an update check. In case of {@code false} and the local file given by
     *            {@link #getFile()} does actually not exist, {@link #setException(RepositoryException)} should be used
     *            to provide the previous/cached failure that explains the absence of the file.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setRequired(boolean required) {
        this.required = required;
        return this;
    }

    /**
     * Gets the exception that occurred during the update check.
     *
     * @return The occurred exception or {@code null} if the update check was successful.
     */
    public E getException() {
        return exception;
    }

    /**
     * Sets the exception for this update check.
     *
     * @param exception The exception for this update check, may be {@code null} if the check was successful.
     * @return This object for chaining.
     */
    public UpdateCheck<T, E> setException(E exception) {
        this.exception = exception;
        return this;
    }

    @Override
    public String toString() {
        return getArtifactPolicy() + "/" + getMetadataPolicy() + ": " + getFile() + " < " + getRepository();
    }
}
