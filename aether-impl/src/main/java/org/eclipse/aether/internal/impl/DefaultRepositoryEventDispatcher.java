/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
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
@Component( role = RepositoryEventDispatcher.class )
public class DefaultRepositoryEventDispatcher
    implements RepositoryEventDispatcher, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement( role = RepositoryListener.class )
    private Collection<RepositoryListener> listeners = new ArrayList<RepositoryListener>();

    public DefaultRepositoryEventDispatcher()
    {
        // enables no-arg constructor
    }

    DefaultRepositoryEventDispatcher( Set<RepositoryListener> listeners )
    {
        setRepositoryListeners( listeners );
    }

    public DefaultRepositoryEventDispatcher setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
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

    DefaultRepositoryEventDispatcher setListeners( List<RepositoryListener> listeners )
    {
        // plexus support
        return setRepositoryListeners( listeners );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setListeners( locator.getServices( RepositoryListener.class ) );
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
