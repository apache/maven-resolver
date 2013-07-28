/*******************************************************************************
 * Copyright (c) 2012, 2013 Sonatype, Inc.
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 */
@Named
@Component( role = RepositoryConnectorProvider.class )
public class DefaultRepositoryConnectorProvider
    implements RepositoryConnectorProvider, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement( role = RepositoryConnectorFactory.class )
    private Collection<RepositoryConnectorFactory> connectorFactories = new ArrayList<RepositoryConnectorFactory>();

    private static final Comparator<RepositoryConnectorFactory> COMPARATOR =
        new Comparator<RepositoryConnectorFactory>()
        {

            public int compare( RepositoryConnectorFactory o1, RepositoryConnectorFactory o2 )
            {
                return Float.compare( o2.getPriority(), o1.getPriority() );
            }

        };

    public DefaultRepositoryConnectorProvider()
    {
        // enables default constructor
    }

    @Inject
    DefaultRepositoryConnectorProvider( Set<RepositoryConnectorFactory> connectorFactories, LoggerFactory loggerFactory )
    {
        setRepositoryConnectorFactories( connectorFactories );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        connectorFactories = locator.getServices( RepositoryConnectorFactory.class );
    }

    public DefaultRepositoryConnectorProvider setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultRepositoryConnectorProvider addRepositoryConnectorFactory( RepositoryConnectorFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "repository connector factory has not been specified" );
        }
        connectorFactories.add( factory );
        return this;
    }

    public DefaultRepositoryConnectorProvider setRepositoryConnectorFactories( Collection<RepositoryConnectorFactory> factories )
    {
        if ( factories == null )
        {
            this.connectorFactories = new ArrayList<RepositoryConnectorFactory>();
        }
        else
        {
            this.connectorFactories = factories;
        }
        return this;
    }

    DefaultRepositoryConnectorProvider setConnectorFactories( List<RepositoryConnectorFactory> factories )
    {
        // plexus support
        return setRepositoryConnectorFactories( factories );
    }

    public RepositoryConnector newRepositoryConnector( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "remote repository has not been specified" );
        }

        List<RepositoryConnectorFactory> factories = new ArrayList<RepositoryConnectorFactory>( connectorFactories );
        Collections.sort( factories, COMPARATOR );

        List<NoRepositoryConnectorException> errors = new ArrayList<NoRepositoryConnectorException>();
        for ( RepositoryConnectorFactory factory : factories )
        {
            try
            {
                RepositoryConnector connector = factory.newInstance( session, repository );

                if ( logger.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using connector " ).append( connector.getClass().getSimpleName() );
                    buffer.append( " with priority " ).append( factory.getPriority() );
                    buffer.append( " for " ).append( repository.getUrl() );

                    Authentication auth = repository.getAuthentication();
                    if ( auth != null )
                    {
                        buffer.append( " with " ).append( auth );
                    }

                    Proxy proxy = repository.getProxy();
                    if ( proxy != null )
                    {
                        buffer.append( " via " ).append( proxy.getHost() ).append( ':' ).append( proxy.getPort() );

                        auth = proxy.getAuthentication();
                        if ( auth != null )
                        {
                            buffer.append( " with " ).append( auth );
                        }
                    }

                    logger.debug( buffer.toString() );
                }

                return connector;
            }
            catch ( NoRepositoryConnectorException e )
            {
                // continue and try next factory
                errors.add( e );
            }
        }
        if ( logger.isDebugEnabled() && errors.size() > 1 )
        {
            String msg = "Could not obtain connector factory for " + repository;
            for ( Exception e : errors )
            {
                logger.debug( msg, e );
            }
        }

        StringBuilder buffer = new StringBuilder( 256 );
        if ( factories.isEmpty() )
        {
            buffer.append( "No connector factories available" );
        }
        else
        {
            buffer.append( "Cannot access " ).append( repository.getUrl() );
            buffer.append( " with type " ).append( repository.getContentType() );
            buffer.append( " using the available connector factories: " );
            for ( ListIterator<RepositoryConnectorFactory> it = factories.listIterator(); it.hasNext(); )
            {
                RepositoryConnectorFactory factory = it.next();
                buffer.append( factory.getClass().getSimpleName() );
                if ( it.hasNext() )
                {
                    buffer.append( ", " );
                }
            }
        }

        throw new NoRepositoryConnectorException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 )
                        : null );
    }

}
