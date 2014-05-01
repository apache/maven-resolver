/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 * Creates local repository managers for repository type {@code "simple"}.
 */
@Named( "simple" )
public class SimpleLocalRepositoryManagerFactory
    implements LocalRepositoryManagerFactory, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private float priority;

    public SimpleLocalRepositoryManagerFactory()
    {
        // enable no-arg constructor
    }

    @Inject
    SimpleLocalRepositoryManagerFactory( LoggerFactory loggerFactory )
    {
        setLoggerFactory( loggerFactory );
    }

    public LocalRepositoryManager newInstance( RepositorySystemSession session, LocalRepository repository )
        throws NoLocalRepositoryManagerException
    {
        if ( "".equals( repository.getContentType() ) || "simple".equals( repository.getContentType() ) )
        {
            return new SimpleLocalRepositoryManager( repository.getBasedir() ).setLogger( logger );
        }
        else
        {
            throw new NoLocalRepositoryManagerException( repository );
        }
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
    }

    public SimpleLocalRepositoryManagerFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, SimpleLocalRepositoryManager.class );
        return this;
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
    public SimpleLocalRepositoryManagerFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

}
