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
package org.eclipse.aether.resolution;

import org.eclipse.aether.repository.RemoteRepository;

/**
 * A query for the resolution error policy for a given artifact/metadata.
 *
 * @param <T> The type of the affected repository item (artifact or metadata).
 * @see ResolutionErrorPolicy
 */
public final class ResolutionErrorPolicyRequest<T> {

    private T item;

    private RemoteRepository repository;

    /**
     * Creates an uninitialized request.
     */
    public ResolutionErrorPolicyRequest() {
        // enables default constructor
    }

    /**
     * Creates a request for the specified artifact/metadata and remote repository.
     *
     * @param item The artifact/metadata for which to determine the error policy, may be {@code null}.
     * @param repository The repository from which the resolution is attempted, may be {@code null}.
     */
    public ResolutionErrorPolicyRequest(T item, RemoteRepository repository) {
        setItem(item);
        setRepository(repository);
    }

    /**
     * Gets the artifact/metadata for which to determine the error policy.
     *
     * @return The artifact/metadata for which to determine the error policy or {@code null} if not set.
     */
    public T getItem() {
        return item;
    }

    /**
     * Sets the artifact/metadata for which to determine the error policy.
     *
     * @param item The artifact/metadata for which to determine the error policy, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ResolutionErrorPolicyRequest<T> setItem(T item) {
        this.item = item;
        return this;
    }

    /**
     * Gets the remote repository from which the resolution of the artifact/metadata is attempted.
     *
     * @return The involved remote repository or {@code null} if not set.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Sets the remote repository from which the resolution of the artifact/metadata is attempted.
     *
     * @param repository The repository from which the resolution is attempted, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ResolutionErrorPolicyRequest<T> setRepository(RemoteRepository repository) {
        this.repository = repository;
        return this;
    }

    @Override
    public String toString() {
        return getItem() + " < " + getRepository();
    }
}
