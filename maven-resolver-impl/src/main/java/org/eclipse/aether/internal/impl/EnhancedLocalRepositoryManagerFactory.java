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

import java.util.Locale;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
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
public class EnhancedLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory, Service {
    public static final String NAME = "enhanced";
    private static final String CONFIG_PROP_TRACKING_FILENAME = "aether.enhancedLocalRepository.trackingFilename";

    private static final String DEFAULT_TRACKING_FILENAME = "_remote.repositories";

    /**
     * The function producing the repository component of the tracking entries this manager writes and consults, and
     * of nothing else: artifact and metadata paths and split local repository prefixes keep following the repository
     * id. Accepted values: {@code "nid_hurl"} (default) qualifies the id with a hash of the repository URL, so two
     * repositories merely sharing an id are tracked as different origins; {@code "nid"} is the id alone, the
     * behavior of earlier releases. Entries written under a different function than the active one never match a
     * lookup and never enable the untracked-file fallback: affected artifacts are treated as locally unavailable and
     * fetched again once.
     */
    private static final String CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION =
            "aether.enhancedLocalRepository.trackingRepositoryKeyFunction";

    private static final String DEFAULT_TRACKING_REPOSITORY_KEY_FUNCTION = "nid_hurl";

    private float priority = 10.0f;

    private LocalPathComposer localPathComposer;

    private TrackingFileManager trackingFileManager;

    private LocalPathPrefixComposerFactory localPathPrefixComposerFactory;

    @Deprecated
    public EnhancedLocalRepositoryManagerFactory() {
        // no arg ctor for ServiceLocator
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
    public void initService(final ServiceLocator locator) {
        this.localPathComposer = requireNonNull(locator.getService(LocalPathComposer.class));
        this.trackingFileManager = requireNonNull(locator.getService(TrackingFileManager.class));
        this.localPathPrefixComposerFactory = new DefaultLocalPathPrefixComposerFactory();
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
                    repository.getBasedir(),
                    localPathComposer,
                    trackingFilename,
                    trackingFileManager,
                    localPathPrefixComposerFactory.createComposer(session),
                    trackingRepositoryKeyFunction(ConfigUtils.getString(
                            session,
                            DEFAULT_TRACKING_REPOSITORY_KEY_FUNCTION,
                            CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION)));
        } else {
            throw new NoLocalRepositoryManagerException(repository);
        }
    }

    static EnhancedLocalRepositoryManager.TrackingKeyFunction trackingRepositoryKeyFunction(String name) {
        try {
            return EnhancedLocalRepositoryManager.TrackingKeyFunction.valueOf(name.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown " + CONFIG_PROP_TRACKING_REPOSITORY_KEY_FUNCTION + " value '" + name
                            + "': expected nid_hurl or nid",
                    e);
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
