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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.remoterepo.RepositoryKeyFunctionFactory;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Creates enhanced local repository managers for repository types {@code "default"} or {@code "" (automatic)}. Enhanced
 * local repository manager is built upon the classical Maven 2.0 local repository structure but additionally keeps
 * track of from what repositories a cached artifact was resolved. Resolution of locally cached artifacts will be
 * rejected in case the current resolution request does not match the known source repositories of an artifact, thereby
 * emulating physically separated artifact caches per remote repository.
 */
@Singleton
@Named(EnhancedLocalRepositoryManagerFactory.NAME)
public class EnhancedLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory {
    public static final String NAME = "enhanced";

    static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_LRM + NAME + ".";

    /**
     * Filename of the file in which to track the remote repositories.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_TRACKING_FILENAME}
     */
    public static final String CONFIG_PROP_TRACKING_FILENAME = CONFIG_PROPS_PREFIX + "trackingFilename";

    public static final String DEFAULT_TRACKING_FILENAME = "_remote.repositories";

    /**
     * Whether to verify that the real (on-disk) path of a locally cached artifact matches the requested path
     * spelling before the artifact is used. On case-insensitive or case/normalization-preserving filesystems (the
     * macOS and Windows defaults) a file cached for one set of coordinates also answers lookups for coordinates
     * that differ only in case or Unicode normalization, while the repository tracking data is compared exactly:
     * such an aliased file is treated as present-but-untracked and accepted with no download and no checksum
     * verification, letting case-colliding coordinates poison distinct GAVs. When enabled (the default), an
     * artifact whose on-disk path spelling differs from the requested one is treated as not present, forcing a
     * proper download. Disable only if the local repository intentionally contains symbolic links below its base
     * directory (a symlinked base directory itself is supported either way).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_VERIFY_REAL_PATH}
     * @since 2.0.23
     */
    public static final String CONFIG_PROP_VERIFY_REAL_PATH = CONFIG_PROPS_PREFIX + "verifyRealPath";

    public static final boolean DEFAULT_VERIFY_REAL_PATH = true;

    private float priority = 10.0f;

    private final LocalPathComposer localPathComposer;

    private final TrackingFileManager trackingFileManager;

    private final LocalPathPrefixComposerFactory localPathPrefixComposerFactory;

    private final RepositoryKeyFunctionFactory repositoryKeyFunctionFactory;

    @Inject
    public EnhancedLocalRepositoryManagerFactory(
            final LocalPathComposer localPathComposer,
            final TrackingFileManager trackingFileManager,
            final LocalPathPrefixComposerFactory localPathPrefixComposerFactory,
            final RepositoryKeyFunctionFactory repositoryKeyFunctionFactory) {
        this.localPathComposer = requireNonNull(localPathComposer);
        this.trackingFileManager = requireNonNull(trackingFileManager);
        this.localPathPrefixComposerFactory = requireNonNull(localPathPrefixComposerFactory);
        this.repositoryKeyFunctionFactory = requireNonNull(repositoryKeyFunctionFactory);
    }

    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        String trackingFilename = ConfigUtils.getString(session, "", CONFIG_PROP_TRACKING_FILENAME);
        if (trackingFilename.isEmpty()
                || trackingFilename.contains("/")
                || trackingFilename.contains("\\")
                || trackingFilename.contains("..")) {
            trackingFilename = DEFAULT_TRACKING_FILENAME;
        }

        if ("".equals(repository.getContentType()) || "default".equals(repository.getContentType())) {
            try {
                return new EnhancedLocalRepositoryManager(
                        repository.getBasePath(),
                        localPathComposer,
                        repositoryKeyFunctionFactory.repositoryKeyFunction(
                                EnhancedLocalRepositoryManagerFactory.class,
                                session,
                                ConfigurationProperties.DEFAULT_REPOSITORY_SYSTEM_REPOSITORY_TRACKING_KEY_FUNCTION,
                                ConfigurationProperties.REPOSITORY_SYSTEM_REPOSITORY_TRACKING_KEY_FUNCTION),
                        trackingFilename,
                        trackingFileManager,
                        localPathPrefixComposerFactory.createComposer(session));
            } catch (IOException e) {
                throw new NoLocalRepositoryManagerException(repository, e);
            }
        } else {
            throw new NoLocalRepositoryManagerException(repository);
        }
    }

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public EnhancedLocalRepositoryManagerFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }
}
