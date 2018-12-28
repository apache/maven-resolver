package org.eclipse.aether.internal.impl;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Named
public final class DefaultRepositoryLayoutProvider
    implements RepositoryLayoutProvider, Service
{

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultRepositoryLayoutProvider.class );

    private Collection<RepositoryLayoutFactory> factories = new ArrayList<>();

    public DefaultRepositoryLayoutProvider()
    {
        // enables default constructor
    }

    @Inject
    DefaultRepositoryLayoutProvider( Set<RepositoryLayoutFactory> layoutFactories )
    {
        setRepositoryLayoutFactories( layoutFactories );
    }

    public void initService( ServiceLocator locator )
    {
        setRepositoryLayoutFactories( locator.getServices( RepositoryLayoutFactory.class ) );
    }

    public DefaultRepositoryLayoutProvider addRepositoryLayoutFactory( RepositoryLayoutFactory factory )
    {
        factories.add( requireNonNull( factory, "layout factory cannot be null" ) );
        return this;
    }

    public DefaultRepositoryLayoutProvider setRepositoryLayoutFactories( Collection<RepositoryLayoutFactory> factories )
    {
        if ( factories == null )
        {
            this.factories = new ArrayList<>();
        }
        else
        {
            this.factories = factories;
        }
        return this;
    }

    public RepositoryLayout newRepositoryLayout( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryLayoutException
    {
        requireNonNull( repository, "remote repository cannot be null" );

        PrioritizedComponents<RepositoryLayoutFactory> factories = new PrioritizedComponents<>( session );
        for ( RepositoryLayoutFactory factory : this.factories )
        {
            factories.add( factory, factory.getPriority() );
        }

        List<NoRepositoryLayoutException> errors = new ArrayList<>();
        for ( PrioritizedComponent<RepositoryLayoutFactory> factory : factories.getEnabled() )
        {
            try
            {
                RepositoryLayout layout = factory.getComponent().newInstance( session, repository );
                return layout;
            }
            catch ( NoRepositoryLayoutException e )
            {
                // continue and try next factory
                errors.add( e );
            }
        }
        if ( LOGGER.isDebugEnabled() && errors.size() > 1 )
        {
            String msg = "Could not obtain layout factory for " + repository;
            for ( Exception e : errors )
            {
                LOGGER.debug( msg, e );
            }
        }

        StringBuilder buffer = new StringBuilder( 256 );
        if ( factories.isEmpty() )
        {
            buffer.append( "No layout factories registered" );
        }
        else
        {
            buffer.append( "Cannot access " ).append( repository.getUrl() );
            buffer.append( " with type " ).append( repository.getContentType() );
            buffer.append( " using the available layout factories: " );
            factories.list( buffer );
        }

        throw new NoRepositoryLayoutException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 )
                        : null );
    }

}
