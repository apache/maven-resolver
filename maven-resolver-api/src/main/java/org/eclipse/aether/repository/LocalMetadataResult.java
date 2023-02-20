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
 * A result from the local repository about the existence of metadata.
 *
 * @see LocalRepositoryManager#find(org.eclipse.aether.RepositorySystemSession, LocalMetadataRequest)
 */
public final class LocalMetadataResult {

    private final LocalMetadataRequest request;

    private File file;

    private boolean stale;

    /**
     * Creates a new result for the specified request.
     *
     * @param request The local metadata request, must not be {@code null}.
     */
    public LocalMetadataResult(LocalMetadataRequest request) {
        this.request = requireNonNull(request, "local metadata request cannot be null");
    }

    /**
     * Gets the request corresponding to this result.
     *
     * @return The corresponding request, never {@code null}.
     */
    public LocalMetadataRequest getRequest() {
        return request;
    }

    /**
     * Gets the file to the requested metadata if the metadata is available in the local repository.
     *
     * @return The file to the requested metadata or {@code null}.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the file to requested metadata.
     *
     * @param file The metadata file, may be {@code null}.
     * @return This result for chaining, never {@code null}.
     */
    public LocalMetadataResult setFile(File file) {
        this.file = file;
        return this;
    }

    /**
     * This value indicates whether the metadata is stale and should be updated.
     *
     * @return {@code true} if the metadata is stale and should be updated, {@code false} otherwise.
     */
    public boolean isStale() {
        return stale;
    }

    /**
     * Sets whether the metadata is stale.
     *
     * @param stale {@code true} if the metadata is stale and should be updated, {@code false} otherwise.
     * @return This result for chaining, never {@code null}.
     */
    public LocalMetadataResult setStale(boolean stale) {
        this.stale = stale;
        return this;
    }

    @Override
    public String toString() {
        return request.toString() + "(" + getFile() + ")";
    }
}
