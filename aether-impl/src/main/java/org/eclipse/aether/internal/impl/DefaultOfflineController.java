/*******************************************************************************
 * Copyright (c) 2012, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;

@Named
public class DefaultOfflineController
    implements OfflineController, Service
{

    static final String CONFIG_PROP_OFFLINE_PROTOCOLS = "aether.offline.protocols";

    static final String CONFIG_PROP_OFFLINE_HOSTS = "aether.offline.hosts";

    private static final Pattern SEP = Pattern.compile( "\\s*,\\s*" );

    private Logger logger = NullLoggerFactory.LOGGER;

    public DefaultOfflineController()
    {
        // enables default constructor
    }

    @Inject
    DefaultOfflineController( LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
    }

    public DefaultOfflineController setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    public void checkOffline( RepositorySystemSession session, RemoteRepository repository )
        throws RepositoryOfflineException
    {
        if ( !session.isOffline() )
        {
            return;
        }

        if ( isOfflineProtocol( session, repository ) || isOfflineHost( session, repository ) )
        {
            return;
        }

        throw new RepositoryOfflineException( repository );
    }

    private boolean isOfflineProtocol( RepositorySystemSession session, RemoteRepository repository )
    {
        String[] protocols = getConfig( session, CONFIG_PROP_OFFLINE_PROTOCOLS );
        if ( protocols != null )
        {
            String protocol = repository.getProtocol();
            if ( protocol.length() > 0 )
            {
                for ( String p : protocols )
                {
                    if ( p.equalsIgnoreCase( protocol ) )
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOfflineHost( RepositorySystemSession session, RemoteRepository repository )
    {
        String[] hosts = getConfig( session, CONFIG_PROP_OFFLINE_HOSTS );
        if ( hosts != null )
        {
            String host = repository.getHost();
            if ( host.length() > 0 )
            {
                for ( String h : hosts )
                {
                    if ( h.equalsIgnoreCase( host ) )
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String[] getConfig( RepositorySystemSession session, String key )
    {
        String value = ConfigUtils.getString( session, "", key ).trim();
        if ( value.length() <= 0 )
        {
            return null;
        }
        return SEP.split( value );
    }

}
