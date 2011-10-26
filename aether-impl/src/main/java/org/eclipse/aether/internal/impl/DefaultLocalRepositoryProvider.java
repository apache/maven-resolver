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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.NullLogger;

/**
 */
@Component( role = LocalRepositoryProvider.class )
public class DefaultLocalRepositoryProvider
    implements LocalRepositoryProvider, Service
{

    @Requirement
    private Logger logger = NullLogger.INSTANCE;

    @Requirement( role = LocalRepositoryManagerFactory.class )
    private List<LocalRepositoryManagerFactory> managerFactories = new ArrayList<LocalRepositoryManagerFactory>();

    private static final Comparator<LocalRepositoryManagerFactory> COMPARATOR =
        new Comparator<LocalRepositoryManagerFactory>()
        {

            public int compare( LocalRepositoryManagerFactory o1, LocalRepositoryManagerFactory o2 )
            {
                return Float.compare( o2.getPriority(), o1.getPriority() );
            }

        };

    public DefaultLocalRepositoryProvider()
    {
        // enables default constructor
    }

    public void initService( ServiceLocator locator )
    {
        setLogger( locator.getService( Logger.class ) );
        setLocalRepositoryManagerFactories( locator.getServices( LocalRepositoryManagerFactory.class ) );
    }

    public DefaultLocalRepositoryProvider setLogger( Logger logger )
    {
        this.logger = ( logger != null ) ? logger : NullLogger.INSTANCE;
        return this;
    }

    public DefaultLocalRepositoryProvider setLocalRepositoryManagerFactories( List<LocalRepositoryManagerFactory> factories )
    {
        if ( factories == null )
        {
            managerFactories = new ArrayList<LocalRepositoryManagerFactory>( 2 );
        }
        else
        {
            managerFactories = factories;
        }
        return this;
    }

    public DefaultLocalRepositoryProvider addLocalRepositoryManagerFactory( LocalRepositoryManagerFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "Local repository manager factory has not been specified." );
        }
        managerFactories.add( factory );
        return this;
    }

    public LocalRepositoryManager newLocalRepositoryManager( LocalRepository localRepository )
        throws NoLocalRepositoryManagerException
    {
        List<LocalRepositoryManagerFactory> factories = new ArrayList<LocalRepositoryManagerFactory>( managerFactories );
        Collections.sort( factories, COMPARATOR );

        for ( LocalRepositoryManagerFactory factory : factories )
        {
            try
            {
                LocalRepositoryManager manager = factory.newInstance( localRepository );

                if ( logger.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using manager " ).append( manager.getClass().getSimpleName() );
                    buffer.append( " with priority " ).append( factory.getPriority() );
                    buffer.append( " for " ).append( localRepository.getBasedir() );

                    logger.debug( buffer.toString() );
                }

                return manager;
            }
            catch ( NoLocalRepositoryManagerException e )
            {
                // continue and try next factory
            }
        }

        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "No manager available for local repository " );
        buffer.append( localRepository.getBasedir() );
        buffer.append( " of type " ).append( localRepository.getContentType() );
        buffer.append( " using the available factories " );
        for ( ListIterator<LocalRepositoryManagerFactory> it = factories.listIterator(); it.hasNext(); )
        {
            LocalRepositoryManagerFactory factory = it.next();
            buffer.append( factory.getClass().getSimpleName() );
            if ( it.hasNext() )
            {
                buffer.append( ", " );
            }
        }

        throw new NoLocalRepositoryManagerException( localRepository, buffer.toString() );
    }

}
