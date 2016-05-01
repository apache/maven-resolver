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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 */
@Named
public class DefaultRepositoryEventDispatcher
    implements RepositoryEventDispatcher, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private Collection<RepositoryListener> listeners = new ArrayList<RepositoryListener>();

    public DefaultRepositoryEventDispatcher()
    {
        // enables no-arg constructor
    }

    @Inject
    DefaultRepositoryEventDispatcher( Set<RepositoryListener> listeners, LoggerFactory loggerFactory )
    {
        setRepositoryListeners( listeners );
        setLoggerFactory( loggerFactory );
    }

    public DefaultRepositoryEventDispatcher setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    public DefaultRepositoryEventDispatcher addRepositoryListener( RepositoryListener listener )
    {
        if ( listener == null )
        {
            throw new IllegalArgumentException( "repository listener has not been specified" );
        }
        this.listeners.add( listener );
        return this;
    }

    public DefaultRepositoryEventDispatcher setRepositoryListeners( Collection<RepositoryListener> listeners )
    {
        if ( listeners == null )
        {
            this.listeners = new ArrayList<RepositoryListener>();
        }
        else
        {
            this.listeners = listeners;
        }
        return this;
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setRepositoryListeners( locator.getServices( RepositoryListener.class ) );
    }

    public void dispatch( RepositoryEvent event )
    {
        if ( !listeners.isEmpty() )
        {
            for ( RepositoryListener listener : listeners )
            {
                dispatch( event, listener );
            }
        }

        RepositoryListener listener = event.getSession().getRepositoryListener();

        if ( listener != null )
        {
            dispatch( event, listener );
        }
    }

    private void dispatch( RepositoryEvent event, RepositoryListener listener )
    {
        try
        {
            switch ( event.getType() )
            {
                case ARTIFACT_DEPLOYED:
                    listener.artifactDeployed( event );
                    break;
                case ARTIFACT_DEPLOYING:
                    listener.artifactDeploying( event );
                    break;
                case ARTIFACT_DESCRIPTOR_INVALID:
                    listener.artifactDescriptorInvalid( event );
                    break;
                case ARTIFACT_DESCRIPTOR_MISSING:
                    listener.artifactDescriptorMissing( event );
                    break;
                case ARTIFACT_DOWNLOADED:
                    listener.artifactDownloaded( event );
                    break;
                case ARTIFACT_DOWNLOADING:
                    listener.artifactDownloading( event );
                    break;
                case ARTIFACT_INSTALLED:
                    listener.artifactInstalled( event );
                    break;
                case ARTIFACT_INSTALLING:
                    listener.artifactInstalling( event );
                    break;
                case ARTIFACT_RESOLVED:
                    listener.artifactResolved( event );
                    break;
                case ARTIFACT_RESOLVING:
                    listener.artifactResolving( event );
                    break;
                case METADATA_DEPLOYED:
                    listener.metadataDeployed( event );
                    break;
                case METADATA_DEPLOYING:
                    listener.metadataDeploying( event );
                    break;
                case METADATA_DOWNLOADED:
                    listener.metadataDownloaded( event );
                    break;
                case METADATA_DOWNLOADING:
                    listener.metadataDownloading( event );
                    break;
                case METADATA_INSTALLED:
                    listener.metadataInstalled( event );
                    break;
                case METADATA_INSTALLING:
                    listener.metadataInstalling( event );
                    break;
                case METADATA_INVALID:
                    listener.metadataInvalid( event );
                    break;
                case METADATA_RESOLVED:
                    listener.metadataResolved( event );
                    break;
                case METADATA_RESOLVING:
                    listener.metadataResolving( event );
                    break;
                default:
                    throw new IllegalStateException( "unknown repository event type " + event.getType() );
            }
        }
        catch ( Exception e )
        {
            logError( e, listener );
        }
        catch ( LinkageError e )
        {
            logError( e, listener );
        }
    }

    private void logError( Throwable e, Object listener )
    {
        String msg =
            "Failed to dispatch repository event to " + listener.getClass().getCanonicalName() + ": " + e.getMessage();

        if ( logger.isDebugEnabled() )
        {
            logger.warn( msg, e );
        }
        else
        {
            logger.warn( msg );
        }
    }

}
