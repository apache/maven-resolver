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
 * Creates dynamic local repository managers for repository types {@code "default"} or {@code "" (automatic)}. This is
 * a completely new local repository implementation with capabilities to split "local" (locally installed) and "remote"
 * (cached from remote) artifacts, and also split release and snapshot artifacts. It is able to split even
 * by origin repositories as well. Resolution of locally cached artifacts will be rejected in case the current
 * resolution request does not match the known source repositories of an artifact. If cache split by origin enabled,
 * it physically separates artifact caches per remote repository, while if split not enabled, similar technique is
 * used as in case of enhanced local repository, will emulate physically separated artifact caches per remote
 * repository.
 *
 * @since TBD
 */
@Singleton
@Named( "dynamic" )
public class DynamicLocalRepositoryManagerFactory
        implements LocalRepositoryManagerFactory, Service
{
    private static final String CONFIG_PROP_COMPOSER = "aether.dynamicLocalRepository.composer";

    private static final String DEFAULT_COMPOSER = SplitDynamicPrefixComposerFactory.NAME;

    private float priority = 11.0f;

    private ArtifactPathComposer artifactPathComposer;

    private Map<String, DynamicPrefixComposerFactory> dynamicPrefixComposerFactories;

    private TrackingFileManager trackingFileManager;

    public DynamicLocalRepositoryManagerFactory()
    {
        // no arg ctor for ServiceLocator
    }

    @Inject
    public DynamicLocalRepositoryManagerFactory( final ArtifactPathComposer artifactPathComposer,
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

        String composerName = ConfigUtils.getString( session, DEFAULT_COMPOSER, CONFIG_PROP_COMPOSER );
        DynamicPrefixComposerFactory composerFactory = dynamicPrefixComposerFactories.get( composerName );
        if ( composerFactory == null )
        {
            throw new IllegalArgumentException( "Unknown composer " + composerName );
        }
        if ( "".equals( repository.getContentType() ) || "default".equals( repository.getContentType() ) )
        {
            return new DynamicLocalRepositoryManager(
                    repository.getBasedir(),
                    artifactPathComposer,
                    session,
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
    public DynamicLocalRepositoryManagerFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

}
