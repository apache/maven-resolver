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
package org.eclipse.aether.transport.wagon;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A transporter factory using <a href="http://maven.apache.org/wagon/" target="_blank">Apache Maven Wagon</a>. Note
 * that this factory merely serves as an adapter to the Wagon API and by itself does not provide any transport services
 * unless one or more wagon implementations are registered at runtime.
 */
@Named( "wagon" )
public final class WagonTransporterFactory
    implements TransporterFactory, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private WagonProvider wagonProvider;

    private WagonConfigurator wagonConfigurator;

    private float priority = -1;

    /**
     * Creates an (uninitialized) instance of this transporter factory. <em>Note:</em> In case of manual instantiation
     * by clients, the new factory needs to be configured via its various mutators before first use or runtime errors
     * will occur.
     */
    public WagonTransporterFactory()
    {
        // enables default constructor
    }

    @Inject
    WagonTransporterFactory( WagonProvider wagonProvider, WagonConfigurator wagonConfigurator,
                             LoggerFactory loggerFactory )
    {
        setWagonProvider( wagonProvider );
        setWagonConfigurator( wagonConfigurator );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setWagonProvider( locator.getService( WagonProvider.class ) );
        setWagonConfigurator( locator.getService( WagonConfigurator.class ) );
    }

    /**
     * Sets the logger factory to use for this component.
     * 
     * @param loggerFactory The logger factory to use, may be {@code null} to disable logging.
     * @return This component for chaining, never {@code null}.
     */
    public WagonTransporterFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, WagonTransporter.class );
        return this;
    }

    /**
     * Sets the wagon provider to use to acquire and release wagon instances.
     * 
     * @param wagonProvider The wagon provider to use, may be {@code null}.
     * @return This factory for chaining, never {@code null}.
     */
    public WagonTransporterFactory setWagonProvider( WagonProvider wagonProvider )
    {
        this.wagonProvider = wagonProvider;
        return this;
    }

    /**
     * Sets the wagon configurator to use to apply provider-specific configuration to wagon instances.
     * 
     * @param wagonConfigurator The wagon configurator to use, may be {@code null}.
     * @return This factory for chaining, never {@code null}.
     */
    public WagonTransporterFactory setWagonConfigurator( WagonConfigurator wagonConfigurator )
    {
        this.wagonConfigurator = wagonConfigurator;
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
    public WagonTransporterFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    public Transporter newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        return new WagonTransporter( wagonProvider, wagonConfigurator, repository, session, logger );
    }

}
