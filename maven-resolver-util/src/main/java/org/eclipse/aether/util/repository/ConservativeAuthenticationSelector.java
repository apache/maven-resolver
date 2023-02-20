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
package org.eclipse.aether.util.repository;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Objects.requireNonNull;

/**
 * An authentication selector that delegates to another selector but only if a repository has no authentication data
 * yet. If authentication has already been assigned to a repository, that is selected.
 */
public final class ConservativeAuthenticationSelector implements AuthenticationSelector {

    private final AuthenticationSelector selector;

    /**
     * Creates a new selector that delegates to the specified selector.
     *
     * @param selector The selector to delegate to in case a repository has no authentication yet, must not be
     *            {@code null}.
     */
    public ConservativeAuthenticationSelector(AuthenticationSelector selector) {
        this.selector = requireNonNull(selector, "authentication selector cannot be null");
    }

    public Authentication getAuthentication(RemoteRepository repository) {
        requireNonNull(repository, "repository cannot be null");
        Authentication auth = repository.getAuthentication();
        if (auth != null) {
            return auth;
        }
        return selector.getAuthentication(repository);
    }
}
