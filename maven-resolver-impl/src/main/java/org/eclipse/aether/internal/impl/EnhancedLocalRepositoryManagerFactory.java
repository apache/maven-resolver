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

    /**
     * Marks whether the local repository is meant to be shared (or was shared) with legacy Maven 3.9 or older
     * versions. Maven 3.9 and older versions suffer from "impostor" problem, where artifact and metadata origin was
     * tracked only by the remote repository ID, where two remote repositories may share same ID but different URLs,
     * in fact they may be completely unrelated to each other (ID clash by mistake), or, it may be due some sort of
     * "impostor" attempt, where a malicious repository may pretend like some other repository.
     * Right now, we intentionally default to {@code true} to ease users transitioning, and Resolver 2 will retain
     * this "old" behavior (will observe legacy tracking entries and will store remote metadata as before). But,
     * at some point in the future, the default value will be changed to {@code false} (and same change is warmly
     * recommended for modern Maven users, who do not intend to share local repository with older Maven versions).
     * When this configuration set to {@code false}, the "repository key" is not ID only anymore, but is changed
     * to {@code $id-sha1($url)} form, and this key is used in "origin tracking" entries and in caching remote
     * Maven Repository Metadata XML files as well, guaranteeing they are not mixed in case of same IDs.
     *
     * @see ConfigurationProperties#REPOSITORY_SYSTEM_REPOSITORY_KEY_FUNCTION
     * @see ConfigurationProperties#REPOSITORY_TRACKING_REPOSITORY_KEY_FUNCTION
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_LEGACY_LOCAL_REPOSITORY}
     * @since 2.0.23
     */
    public static final String CONFIG_PROP_LEGACY_LOCAL_REPOSITORY = CONFIG_PROPS_PREFIX + "legacyLocalRepository";

    public static final boolean DEFAULT_LEGACY_LOCAL_REPOSITORY = true;

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
        boolean legacyLocalRepository =
                ConfigUtils.getBoolean(session, DEFAULT_LEGACY_LOCAL_REPOSITORY, CONFIG_PROP_LEGACY_LOCAL_REPOSITORY);

        if ("".equals(repository.getContentType()) || "default".equals(repository.getContentType())) {
            try {
                return new EnhancedLocalRepositoryManager(
                        repository.getBasePath(),
                        localPathComposer,
                        repositoryKeyFunctionFactory.trackingRepositoryKeyFunction(session),
                        trackingFilename,
                        legacyLocalRepository,
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
