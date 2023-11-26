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
package org.eclipse.aether.internal.impl.synccontext.named;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.StringDigestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Wrapping {@link NameMapper}, that wraps another {@link NameMapper} and adds a "discriminator" as prefix, that
 * makes lock names unique including the hostname and local repository (by default). The discriminator may be passed
 * in via {@link RepositorySystemSession} or is automatically calculated based on the local hostname and repository
 * path. The implementation retains order of collection elements as it got it from
 * {@link NameMapper#nameLocks(RepositorySystemSession, Collection, Collection)} method.
 * <p>
 * The default setup wraps {@link GAVNameMapper}, but manually may be created any instance needed.
 */
public class DiscriminatingNameMapper implements NameMapper {
    /**
     * Configuration property to pass in discriminator, if needed. If not present, it is auto-calculated.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_DISCRIMINATOR =
            NamedLockFactoryAdapter.CONFIG_PROPS_PREFIX + "discriminating.discriminator";

    /**
     * Configuration property to pass in hostname, if needed. If not present, hostname as reported by system will be
     * used.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_HOSTNAME =
            NamedLockFactoryAdapter.CONFIG_PROPS_PREFIX + "discriminating.hostname";

    private static final String DEFAULT_DISCRIMINATOR_DIGEST = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    private static final String DEFAULT_HOSTNAME = "localhost";

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscriminatingNameMapper.class);

    private final NameMapper delegate;

    private final String hostname;

    public DiscriminatingNameMapper(final NameMapper delegate) {
        this.delegate = requireNonNull(delegate);
        this.hostname = getHostname();
    }

    @Override
    public boolean isFileSystemFriendly() {
        return false; // uses ":" in produced lock names
    }

    @Override
    public Collection<String> nameLocks(
            final RepositorySystemSession session,
            final Collection<? extends Artifact> artifacts,
            final Collection<? extends Metadata> metadatas) {
        String discriminator = createDiscriminator(session);
        return delegate.nameLocks(session, artifacts, metadatas).stream()
                .map(s -> discriminator + ":" + s)
                .collect(toList());
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOGGER.warn("Failed to get hostname, using '{}'", DEFAULT_HOSTNAME, e);
            return DEFAULT_HOSTNAME;
        }
    }

    private String createDiscriminator(final RepositorySystemSession session) {
        String discriminator = ConfigUtils.getString(session, null, CONFIG_PROP_DISCRIMINATOR);

        if (discriminator == null || discriminator.isEmpty()) {
            String hostname = ConfigUtils.getString(session, this.hostname, CONFIG_PROP_HOSTNAME);
            File basedir = session.getLocalRepository().getBasedir();
            discriminator = hostname + ":" + basedir;
            try {
                return StringDigestUtil.sha1(discriminator);
            } catch (Exception e) {
                LOGGER.warn("Failed to calculate discriminator digest, using '{}'", DEFAULT_DISCRIMINATOR_DIGEST, e);
                return DEFAULT_DISCRIMINATOR_DIGEST;
            }
        }
        return discriminator;
    }
}
