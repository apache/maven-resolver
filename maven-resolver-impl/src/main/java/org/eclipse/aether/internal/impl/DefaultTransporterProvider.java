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
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.transfer.NoTransporterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Singleton
@Named
public final class DefaultTransporterProvider
    implements TransporterProvider
{

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultTransporterProvider.class );

    private final Collection<TransporterFactory> factories;

    @Inject
    public DefaultTransporterProvider( Set<TransporterFactory> transporterFactories )
    {
        this.factories = new ArrayList<>();
        if ( transporterFactories != null )
        {
            this.factories.addAll( transporterFactories );
        }
    }

    public DefaultTransporterProvider addTransporterFactory( TransporterFactory factory )
    {
        factories.add( requireNonNull( factory, "transporter factory cannot be null" ) );
        return this;
    }

    @Override
    public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        requireNonNull( repository, "remote repository cannot be null" );

        PrioritizedComponents<TransporterFactory> factories = new PrioritizedComponents<>( session );
        for ( TransporterFactory factory : this.factories )
        {
            factories.add( factory, factory.getPriority() );
        }

        List<NoTransporterException> errors = new ArrayList<>();
        for ( PrioritizedComponent<TransporterFactory> factory : factories.getEnabled() )
        {
            try
            {
                Transporter transporter = factory.getComponent().newInstance( session, repository );

                if ( LOGGER.isDebugEnabled() )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    buffer.append( "Using transporter " ).append( transporter.getClass().getSimpleName() );
                    Utils.appendClassLoader( buffer, transporter );
                    buffer.append( " with priority " ).append( factory.getPriority() );
                    buffer.append( " for " ).append( repository.getUrl() );
                    LOGGER.debug( buffer.toString() );
                }

                return transporter;
            }
            catch ( NoTransporterException e )
            {
                // continue and try next factory
                errors.add( e );
            }
        }
        if ( LOGGER.isDebugEnabled() && errors.size() > 1 )
        {
            for ( Exception e : errors )
            {
                LOGGER.debug( "Could not obtain transporter factory for {}", repository, e );
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
            factories.list( buffer );
        }

        throw new NoTransporterException( repository, buffer.toString(), errors.size() == 1 ? errors.get( 0 ) : null );
    }

}
