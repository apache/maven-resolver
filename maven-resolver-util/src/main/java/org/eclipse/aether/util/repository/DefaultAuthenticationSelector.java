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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationScope;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.AuthenticationSelectorV2;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Objects.requireNonNull;

/**
 * A simple authentication selector that selects authentication based on repository identifiers.
 */
public final class DefaultAuthenticationSelector implements AuthenticationSelector, AuthenticationSelectorV2 {

    private final Map<String, Authentication> reposById = new HashMap<>();
    private final Map<AuthenticationScope, Authentication> reposByScope = new HashMap<>();
    
    /**
     * Adds the specified authentication info for the given repository identifier.
     *
     * @param id The identifier of the repository to add the authentication for, must not be {@code null}.
     * @param auth The authentication to add, may be {@code null}.
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultAuthenticationSelector add(String id, Authentication auth) {
        if (auth != null) {
            reposById.put(id, auth);
        } else {
            reposById.remove(id);
        }

        return this;
    }

    /**
     * Adds the specified authentication info for the given repository identifier.
     *
     * @param scope The scope to add the authentication for, must not be {@code null}.
     * @param auth The authentication to add, may be {@code null}.
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultAuthenticationSelector add(AuthenticationScope scope, Authentication auth) {
        if (auth != null) {
            reposByScope.put(scope, auth);
        } else {
            reposByScope.remove(scope);
        }

        return this;
    }

    public Authentication getAuthentication(RemoteRepository repository) {
        requireNonNull(repository, "repository cannot be null");
        return reposById.get(repository.getId());
    }

    @Override
    public Authentication getAuthentication(URI uri, String scheme, String realm) {
        requireNonNull(uri, "uri cannot be null");
        return reposByScope.entrySet().stream().filter(e -> e.getKey().isMatching(uri, scheme, realm)).map(Entry::getValue).findFirst().orElse(null);
    }
}
