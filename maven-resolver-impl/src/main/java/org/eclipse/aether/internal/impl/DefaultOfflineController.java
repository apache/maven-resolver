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
package org.eclipse.aether.internal.impl;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.regex.Pattern;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 *
 */
@Singleton
@Named
public class DefaultOfflineController implements OfflineController {
    private static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "offline.";

    /**
     * Comma-separated list of protocols which are supposed to be accessed when session is offline, or be unreachable
     * when session is online.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_OFFLINE_PROTOCOLS = CONFIG_PROPS_PREFIX + "protocols";

    /**
     * Comma-separated list of hosts which are supposed to be resolved when session is offline, or be unreachable
     * when session is online.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_OFFLINE_HOSTS = CONFIG_PROPS_PREFIX + "hosts";

    /**
     * Comma-separated list of repository IDs which are supposed to be resolved when session is offline, or be
     * unreachable when session is online.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @since TBD
     */
    public static final String CONFIG_PROP_OFFLINE_REPOSITORIES = CONFIG_PROPS_PREFIX + "repositories";

    private static final Pattern SEP = Pattern.compile("\\s*,\\s*");

    @Override
    public void checkOffline(RepositorySystemSession session, RemoteRepository repository)
            throws RepositoryOfflineException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        if (isOfflineProtocol(session, repository)
                || isOfflineHost(session, repository)
                || isOfflineRepository(session, repository)) {
            return;
        }

        throw new RepositoryOfflineException(repository);
    }

    private boolean isOfflineProtocol(RepositorySystemSession session, RemoteRepository repository) {
        String[] protocols = getConfig(session, CONFIG_PROP_OFFLINE_PROTOCOLS);
        if (protocols != null) {
            String protocol = repository.getProtocol();
            if (!protocol.isEmpty()) {
                for (String p : protocols) {
                    if (p.equalsIgnoreCase(protocol)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOfflineHost(RepositorySystemSession session, RemoteRepository repository) {
        String[] hosts = getConfig(session, CONFIG_PROP_OFFLINE_HOSTS);
        if (hosts != null) {
            String host = repository.getHost();
            if (!host.isEmpty()) {
                for (String h : hosts) {
                    if (h.equalsIgnoreCase(host)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOfflineRepository(RepositorySystemSession session, RemoteRepository repository) {
        String[] repositories = getConfig(session, CONFIG_PROP_OFFLINE_REPOSITORIES);
        if (repositories != null) {
            String repositoryId = repository.getId();
            if (!repositoryId.isEmpty()) {
                for (String r : repositories) {
                    if (r.equals(repositoryId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String[] getConfig(RepositorySystemSession session, String key) {
        String value = ConfigUtils.getString(session, "", key).trim();
        if (value.isEmpty()) {
            return null;
        }
        return SEP.split(value);
    }
}
