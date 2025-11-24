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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.repository.RepositoryIdHelper;

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
     * <b>Experimental:</b> Configuration for "repository key" function.
     * Note: repository key functions other than "simple" produce repository keys will be <em>way different
     * that those produced with previous versions or without this option enabled</em>. Ideally, you may want to
     * use empty local repository to populate with new repository key contained metadata.
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_REPOSITORY_KEY_FUNCTION}
     */
    public static final String CONFIG_PROP_REPOSITORY_KEY_FUNCTION = CONFIG_PROPS_PREFIX + "repositoryKeyFunction";

    public static final String DEFAULT_REPOSITORY_KEY_FUNCTION = "simple";

    private float priority = 10.0f;

    private final LocalPathComposer localPathComposer;

    private final TrackingFileManager trackingFileManager;

    private final LocalPathPrefixComposerFactory localPathPrefixComposerFactory;

    /**
     * Method that based on configuration returns the "repository key function". Used by {@link EnhancedLocalRepositoryManagerFactory}
     * and {@link LocalPathPrefixComposerFactory}.
     *
     * @since 2.0.14
     */
    @SuppressWarnings("unchecked")
    public static BiFunction<RemoteRepository, String, String> repositoryKeyFunction(RepositorySystemSession session) {
        final RepositoryIdHelper.RepositoryKeyFunction repositoryKeyFunction =
                RepositoryIdHelper.getRepositoryKeyFunction(ConfigUtils.getString(
                        session, DEFAULT_REPOSITORY_KEY_FUNCTION, CONFIG_PROP_REPOSITORY_KEY_FUNCTION));
        if (session.getCache() != null) {
            // both are expensive methods; cache it in session (repo -> context -> ID)
            return (repository, context) -> ((ConcurrentMap<RemoteRepository, ConcurrentMap<String, String>>)
                            session.getCache()
                                    .computeIfAbsent(
                                            session,
                                            EnhancedLocalRepositoryManagerFactory.class.getName()
                                                    + ".repositoryKeyFunction",
                                            ConcurrentHashMap::new))
                    .computeIfAbsent(repository, k1 -> new ConcurrentHashMap<>())
                    .computeIfAbsent(
                            context == null ? "" : context, k2 -> repositoryKeyFunction.apply(repository, context));
        } else {
            return repositoryKeyFunction;
        }
    }

    @Inject
    public EnhancedLocalRepositoryManagerFactory(
            final LocalPathComposer localPathComposer,
            final TrackingFileManager trackingFileManager,
            final LocalPathPrefixComposerFactory localPathPrefixComposerFactory) {
        this.localPathComposer = requireNonNull(localPathComposer);
        this.trackingFileManager = requireNonNull(trackingFileManager);
        this.localPathPrefixComposerFactory = requireNonNull(localPathPrefixComposerFactory);
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
            return new EnhancedLocalRepositoryManager(
                    repository.getBasePath(),
                    localPathComposer,
                    repositoryKeyFunction(session),
                    trackingFilename,
                    trackingFileManager,
                    localPathPrefixComposerFactory.createComposer(session));
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
