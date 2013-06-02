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
import org.eclipse.aether.spi.connector.layout.NoRepositoryLayoutException;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 */
@Named
@Component( role = RepositoryLayoutProvider.class )
public final class DefaultRepositoryLayoutProvider
    implements RepositoryLayoutProvider, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement( role = RepositoryLayoutFactory.class )
    private Collection<RepositoryLayoutFactory> factories = new ArrayList<RepositoryLayoutFactory>();

    private static final Comparator<RepositoryLayoutFactory> COMPARATOR = new Comparator<RepositoryLayoutFactory>()
    {

        public int compare( RepositoryLayoutFactory o1, RepositoryLayoutFactory o2 )
        {
            return Float.compare( o2.getPriority(), o1.getPriority() );
        }

    };

    public DefaultRepositoryLayoutProvider()
    {
        // enables default constructor
    }

    @Inject
    DefaultRepositoryLayoutProvider( Set<RepositoryLayoutFactory> layoutFactories, LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
        setRepositoryLayoutFactories( layoutFactories );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setRepositoryLayoutFactories( locator.getServices( RepositoryLayoutFactory.class ) );
    }

    public DefaultRepositoryLayoutProvider setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultRepositoryLayoutProvider addRepositoryLayoutFactory( RepositoryLayoutFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "layout factory has not been specified" );
        }
        factories.add( factory );
        return this;
    }

    public DefaultRepositoryLayoutProvider setRepositoryLayoutFactories( Collection<RepositoryLayoutFactory> factories )
    {
        if ( factories == null )
        {
            this.factories = new ArrayList<RepositoryLayoutFactory>();
        }
        else
        {
            this.factories = factories;
        }
        return this;
    }

    DefaultRepositoryLayoutProvider setFactories( List<RepositoryLayoutFactory> factories )
    {
        // plexus support
        return setRepositoryLayoutFactories( factories );
    }

    public RepositoryLayout newRepositoryLayout( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryLayoutException
    {
        if ( repository == null )
        {
            throw new IllegalArgumentException( "remote repository has not been specified" );
        }

        List<RepositoryLayoutFactory> factories = new ArrayList<RepositoryLayoutFactory>( this.factories );
        Collections.sort( factories, COMPARATOR );

        for ( RepositoryLayoutFactory factory : factories )
        {
            try
            {
                RepositoryLayout layout = factory.newInstance( session, repository );
                return layout;
            }
            catch ( NoRepositoryLayoutException e )
            {
                // continue and try next factory
            }
        }

        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "No layout available to access repository " );
        buffer.append( repository.getId() );
        buffer.append( " (" ).append( repository.getUrl() );
        buffer.append( ") using the available factories " );
        for ( ListIterator<RepositoryLayoutFactory> it = factories.listIterator(); it.hasNext(); )
        {
            RepositoryLayoutFactory factory = it.next();
            buffer.append( factory.getClass().getSimpleName() );
            if ( it.hasNext() )
            {
                buffer.append( ", " );
            }
        }

        throw new NoRepositoryLayoutException( repository, buffer.toString() );
    }

}
