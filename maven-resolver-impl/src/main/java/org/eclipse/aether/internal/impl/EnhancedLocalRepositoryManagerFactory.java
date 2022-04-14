package org.eclipse.aether.internal.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

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
@Named( "enhanced" )
public class EnhancedLocalRepositoryManagerFactory
    implements LocalRepositoryManagerFactory, Service
{
    private static final String CONFIG_PROP_COMPOSER = "aether.dynamicLocalRepository.composer";

    private static final String DEFAULT_COMPOSER = NoopDynamicPrefixComposerFactory.NAME;

    private static final String CONFIG_PROP_TRACKING_FILENAME = "aether.enhancedLocalRepository.trackingFilename";

    private static final String DEFAULT_TRACKING_FILENAME = "_remote.repositories";

    private float priority = 10.0f;

    private ArtifactPathComposer artifactPathComposer;

    private TrackingFileManager trackingFileManager;

    private Map<String, DynamicPrefixComposerFactory> dynamicPrefixComposerFactories;

    public EnhancedLocalRepositoryManagerFactory()
    {
        // no arg ctor for ServiceLocator
    }

    @Inject
    public EnhancedLocalRepositoryManagerFactory( final ArtifactPathComposer artifactPathComposer,
                   final TrackingFileManager trackingFileManager,
                   final Map<String, DynamicPrefixComposerFactory> dynamicPrefixComposerFactories )
    {
        this.artifactPathComposer = requireNonNull( artifactPathComposer );
        this.trackingFileManager = requireNonNull( trackingFileManager );
        this.dynamicPrefixComposerFactories = requireNonNull( dynamicPrefixComposerFactories );
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        this.artifactPathComposer = requireNonNull( locator.getService( ArtifactPathComposer.class ) );
        this.trackingFileManager = requireNonNull( locator.getService( TrackingFileManager.class ) );
        this.dynamicPrefixComposerFactories = new HashMap<>();
        this.dynamicPrefixComposerFactories.put(
                NoopDynamicPrefixComposerFactory.NAME,
                new NoopDynamicPrefixComposerFactory()
        );
        this.dynamicPrefixComposerFactories.put(
                SplitDynamicPrefixComposerFactory.NAME,
                new SplitDynamicPrefixComposerFactory()
        );
        this.dynamicPrefixComposerFactories.put(
                SplitRepositoryDynamicPrefixComposerFactory.NAME,
                new SplitRepositoryDynamicPrefixComposerFactory()
        );
    }

    @Override
    public LocalRepositoryManager newInstance( RepositorySystemSession session, LocalRepository repository )
        throws NoLocalRepositoryManagerException
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( repository, "repository cannot be null" );

        String trackingFilename = ConfigUtils.getString( session, "", CONFIG_PROP_TRACKING_FILENAME );
        if ( trackingFilename.isEmpty() || trackingFilename.contains( "/" ) || trackingFilename.contains( "\\" )
                || trackingFilename.contains( ".." ) )
        {
            trackingFilename = "_remote.repositories";
        }

        String composerName = ConfigUtils.getString( session, DEFAULT_COMPOSER, CONFIG_PROP_COMPOSER );
        DynamicPrefixComposerFactory composerFactory = dynamicPrefixComposerFactories.get( composerName );
        if ( composerFactory == null )
        {
            throw new IllegalArgumentException( "Unknown composer " + composerName );
        }
        if ( "".equals( repository.getContentType() ) || "default".equals( repository.getContentType() ) )
        {
            return new EnhancedLocalRepositoryManager(
                    repository.getBasedir(),
                    artifactPathComposer,
                    trackingFilename,
                    trackingFileManager,
                    composerFactory.createComposer( session )
            );
        }
        else
        {
            throw new NoLocalRepositoryManagerException( repository );
        }
    }

    @Override
    public float getPriority()
    {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public EnhancedLocalRepositoryManagerFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

}
