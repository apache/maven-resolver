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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

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
public class DefaultRepositoryConnectorProvider
    implements RepositoryConnectorProvider, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private Collection<RepositoryConnectorFactory> connectorFactories = new ArrayList<RepositoryConnectorFactory>();

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

    public RepositoryConnector newRepositoryConnector( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "remote repository has not been specified" );
        }

        PrioritizedComponents<RepositoryConnectorFactory> factories =
            new PrioritizedComponents<RepositoryConnectorFactory>( session );
        for ( RepositoryConnectorFactory factory : this.connectorFactories )
        {
            factories.add( factory, factory.getPriority() );
        }

        List<NoRepositoryConnectorException> errors = new ArrayList<NoRepositoryConnectorException>();
        for ( PrioritizedComponent<RepositoryConnectorFactory> factory : factories.getEnabled() )
        {
            try
            {
                RepositoryConnector connector = factory.getComponent().newInstance( session, repository );

                if ( logger.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using connector " ).append( connector.getClass().getSimpleName() );
                    Utils.appendClassLoader( buffer, connector );
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
            factories.list( buffer );
        }

        throw new NoRepositoryConnectorException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 )
                        : null );
    }

}
