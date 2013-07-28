/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
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
@Component( role = TransporterProvider.class )
public final class DefaultTransporterProvider
    implements TransporterProvider, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement( role = TransporterFactory.class )
    private Collection<TransporterFactory> factories = new ArrayList<TransporterFactory>();

    private static final Comparator<TransporterFactory> COMPARATOR = new Comparator<TransporterFactory>()
    {

        public int compare( TransporterFactory o1, TransporterFactory o2 )
        {
            return Float.compare( o2.getPriority(), o1.getPriority() );
        }

    };

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

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
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

    DefaultTransporterProvider setFactories( List<TransporterFactory> factories )
    {
        // plexus support
        return setTransporterFactories( factories );
    }

    public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "remote repository has not been specified" );
        }

        List<TransporterFactory> factories = new ArrayList<TransporterFactory>( this.factories );
        Collections.sort( factories, COMPARATOR );

        List<NoTransporterException> errors = new ArrayList<NoTransporterException>();
        for ( TransporterFactory factory : factories )
        {
            try
            {
                Transporter transporter = factory.newInstance( session, repository );

                if ( logger.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using transporter " ).append( transporter.getClass().getSimpleName() );
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
            for ( ListIterator<TransporterFactory> it = factories.listIterator(); it.hasNext(); )
            {
                TransporterFactory factory = it.next();
                buffer.append( factory.getClass().getSimpleName() );
                if ( it.hasNext() )
                {
                    buffer.append( ", " );
                }
            }
        }

        throw new NoTransporterException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 ) : null );
    }

}
