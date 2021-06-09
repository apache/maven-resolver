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

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;

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
    private float priority = 10.0f;

    private TrackingFileManager trackingFileManager;

    public EnhancedLocalRepositoryManagerFactory()
    {
        // no arg ctor for ServiceLocator
    }

    @Inject
    public EnhancedLocalRepositoryManagerFactory( final TrackingFileManager trackingFileManager )
    {
        this.trackingFileManager = Objects.requireNonNull( trackingFileManager );
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        this.trackingFileManager = Objects.requireNonNull( locator.getService( TrackingFileManager.class ) );
    }

    public LocalRepositoryManager newInstance( RepositorySystemSession session, LocalRepository repository )
        throws NoLocalRepositoryManagerException
    {
        Objects.requireNonNull( "session", "session cannot be null" );
        Objects.requireNonNull( "repository", "repository cannot be null" );

        if ( "".equals( repository.getContentType() ) || "default".equals( repository.getContentType() ) )
        {
            return new EnhancedLocalRepositoryManager( repository.getBasedir(), session, trackingFileManager );
        }
        else
        {
            throw new NoLocalRepositoryManagerException( repository );
        }
    }

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
