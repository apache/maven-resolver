/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 */
@Named
public final class DefaultTransporterProvider
    implements TransporterProvider, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private Collection<TransporterFactory> factories = new ArrayList<TransporterFactory>();

    public DefaultTransporterProvider()
    {
        // enables default constructor
    }

    @Inject
    DefaultTransporterProvider( Set<TransporterFactory> transporterFactories, LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
        setTransporterFactories( transporterFactories );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setTransporterFactories( locator.getServices( TransporterFactory.class ) );
    }

    public DefaultTransporterProvider setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    public DefaultTransporterProvider addTransporterFactory( TransporterFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "transporter factory has not been specified" );
        }
        factories.add( factory );
        return this;
    }

    public DefaultTransporterProvider setTransporterFactories( Collection<TransporterFactory> factories )
    {
        if ( factories == null )
        {
            this.factories = new ArrayList<TransporterFactory>();
        }
        else
        {
            this.factories = factories;
        }
        return this;
    }

    public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "remote repository has not been specified" );
        }

        PrioritizedComponents<TransporterFactory> factories = new PrioritizedComponents<TransporterFactory>( session );
        for ( TransporterFactory factory : this.factories )
        {
            factories.add( factory, factory.getPriority() );
        }

        List<NoTransporterException> errors = new ArrayList<NoTransporterException>();
        for ( PrioritizedComponent<TransporterFactory> factory : factories.getEnabled() )
        {
            try
            {
                Transporter transporter = factory.getComponent().newInstance( session, repository );

                if ( logger.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using transporter " ).append( transporter.getClass().getSimpleName() );
                    Utils.appendClassLoader( buffer, transporter );
                    buffer.append( " with priority " ).append( factory.getPriority() );
                    buffer.append( " for " ).append( repository.getUrl() );
                    logger.debug( buffer.toString() );
                }

                return transporter;
            }
            catch ( NoTransporterException e )
            {
                // continue and try next factory
                errors.add( e );
            }
        }
        if ( logger.isDebugEnabled() && errors.size() > 1 )
        {
            String msg = "Could not obtain transporter factory for " + repository;
            for ( Exception e : errors )
            {
                logger.debug( msg, e );
            }
        }

        StringBuilder buffer = new StringBuilder( 256 );
        if ( factories.isEmpty() )
        {
            buffer.append( "No transporter factories registered" );
        }
        else
        {
            buffer.append( "Cannot access " ).append( repository.getUrl() );
            buffer.append( " using the registered transporter factories: " );
            factories.list( buffer);
        }

        throw new NoTransporterException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 ) : null );
    }

}
