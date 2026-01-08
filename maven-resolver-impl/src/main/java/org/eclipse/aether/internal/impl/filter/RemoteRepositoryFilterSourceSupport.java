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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.impl.checksum.FileTrustedChecksumsSourceSupport;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
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
     * <b>Experimental:</b> Configuration for "repository key" function.
     * Note: repository key functions other than "nid" produce repository keys will be <em>way different
     * that those produced with previous versions or without this option enabled</em>. Filter uses this key function to
     * lay down and look up files to use in filtering.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_REPOSITORY_KEY_FUNCTION}
     */
    public static final String CONFIG_PROP_REPOSITORY_KEY_FUNCTION = CONFIG_PROPS_PREFIX + "repositoryKeyFunction";

    public static final String DEFAULT_REPOSITORY_KEY_FUNCTION = "nid";

    private final RepositoryKeyFunctionFactory repositoryKeyFunctionFactory;

    protected RemoteRepositoryFilterSourceSupport(RepositoryKeyFunctionFactory repositoryKeyFunctionFactory) {
        this.repositoryKeyFunctionFactory = requireNonNull(repositoryKeyFunctionFactory);
    }

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
     * We use remote repositories as keys, so normalize them.
     *
     * @since 2.0.14
     * @see RemoteRepository#toBareRemoteRepository()
     */
    protected RemoteRepository normalizeRemoteRepository(
            RepositorySystemSession session, RemoteRepository remoteRepository) {
        return remoteRepository.toBareRemoteRepository();
    }

    /**
     * Returns repository key to be used on file system layout.
     *
     * @since 2.0.14
     */
    protected String repositoryKey(RepositorySystemSession session, RemoteRepository repository) {
        return repositoryKeyFunctionFactory
                .repositoryKeyFunction(
                        FileTrustedChecksumsSourceSupport.class,
                        session,
                        DEFAULT_REPOSITORY_KEY_FUNCTION,
                        CONFIG_PROP_REPOSITORY_KEY_FUNCTION)
                .apply(repository, null);
    }

    /**
     * Simple {@link RemoteRepositoryFilter.Result} immutable implementation.
     */
    private static class SimpleResult implements RemoteRepositoryFilter.Result {
        private final boolean accepted;

        private final String reasoning;

        private SimpleResult(boolean accepted, String reasoning) {
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

    /**
     * Visible for testing.
     */
    static RemoteRepositoryFilter.Result result(boolean accepted, String name, String message) {
        return new SimpleResult(accepted, name + ": " + message);
    }
}
