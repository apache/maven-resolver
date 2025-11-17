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
package org.eclipse.aether.internal.impl.filter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.eclipse.aether.util.DirectoryUtils;

import static java.util.Objects.requireNonNull;

/**
 * Support class for {@link RemoteRepositoryFilterSource} implementations.
 * <p>
 * Support class for implementing {@link RemoteRepositoryFilterSource}. It implements basic support
 * like optional "basedir" calculation, handling of "enabled" flag.
 * <p>
 * The configuration keys supported:
 * <ul>
 *     <li><pre>aether.remoteRepositoryFilter.${id}.enabled</pre> (boolean) must be explicitly set to "true"
 *     to become enabled</li>
 *     <li><pre>aether.remoteRepositoryFilter.${id}.basedir</pre> (string, path) directory from where implementation
 *     can use files. If unset, default value is ".remoteRepositoryFilters/${id}" and is resolved from local
 *     repository basedir.</li>
 * </ul>
 *
 * @since 1.9.0
 */
public abstract class RemoteRepositoryFilterSourceSupport implements RemoteRepositoryFilterSource {
    protected static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_AETHER + "remoteRepositoryFilter.";

    /**
     * Returns {@code true} if session configuration contains this name set to {@code true}.
     * <p>
     * Default is {@code true}.
     */
    protected abstract boolean isEnabled(RepositorySystemSession session);

    /**
     * Uses common {@link DirectoryUtils#resolveDirectory(RepositorySystemSession, String, String, boolean)} to
     * calculate (and maybe create) basedir for this implementation, never returns {@code null}. The returned
     * {@link Path} may not exists, if invoked with {@code mayCreate} being {@code false}.
     * <p>
     * Default value is {@code ${LOCAL_REPOSITORY}/.checksums}.
     *
     * @return The {@link Path} of basedir, never {@code null}.
     */
    protected Path getBasedir(
            RepositorySystemSession session, String defaultValue, String configPropKey, boolean mayCreate) {
        try {
            return DirectoryUtils.resolveDirectory(session, defaultValue, configPropKey, mayCreate);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * We use remote repositories as keys, but they may fly in as "bare" or as "equipped" (w/ auth and proxy) if caller
     * used {@link org.eclipse.aether.RepositorySystem#newResolutionRepositories(RepositorySystemSession, List)} beforehand.
     * The hash/equalTo method factors in all these as well, but from our perspective, they do not matter. So we make all
     * key remote repositories back to "bare".
     */
    protected RemoteRepository normalizeRemoteRepository(
            RepositorySystemSession session, RemoteRepository remoteRepository) {
        return new RemoteRepository.Builder(remoteRepository)
                .setProxy(null)
                .setAuthentication(null)
                .setMirroredRepositories(null)
                .setRepositoryManager(false)
                .build();
    }

    /**
     * Simple {@link RemoteRepositoryFilter.Result} immutable implementation.
     */
    protected static class SimpleResult implements RemoteRepositoryFilter.Result {
        private final boolean accepted;

        private final String reasoning;

        public SimpleResult(boolean accepted, String reasoning) {
            this.accepted = accepted;
            this.reasoning = requireNonNull(reasoning);
        }

        @Override
        public boolean isAccepted() {
            return accepted;
        }

        @Override
        public String reasoning() {
            return reasoning;
        }
    }
}
