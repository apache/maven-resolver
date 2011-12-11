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
package org.eclipse.aether.connector.wagon;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * A repository connector factory that uses Maven Wagon for the transfers.
 */
@Named
@Component( role = RepositoryConnectorFactory.class, hint = "wagon" )
public final class WagonRepositoryConnectorFactory
    implements RepositoryConnectorFactory, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private FileProcessor fileProcessor;

    @Requirement
    private WagonProvider wagonProvider;

    @Requirement
    private WagonConfigurator wagonConfigurator;

    private float priority;

    public WagonRepositoryConnectorFactory()
    {
        // enables default constructor
    }

    @Inject
    WagonRepositoryConnectorFactory( FileProcessor fileProcessor, WagonProvider wagonProvider,
                                     WagonConfigurator wagonConfigurator, LoggerFactory loggerFactory )
    {
        setFileProcessor( fileProcessor );
        setWagonProvider( wagonProvider );
        setWagonConfigurator( wagonConfigurator );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
        setWagonProvider( locator.getService( WagonProvider.class ) );
        setWagonConfigurator( locator.getService( WagonConfigurator.class ) );
    }

    /**
     * Sets the logger factory to use for this component.
     * 
     * @param loggerFactory The logger factory to use, may be {@code null} to disable logging.
     * @return This component for chaining, never {@code null}.
     */
    public WagonRepositoryConnectorFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, WagonRepositoryConnector.class );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    /**
     * Sets the file processor to use for this component.
     * 
     * @param fileProcessor The file processor to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public WagonRepositoryConnectorFactory setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
        return this;
    }

    /**
     * Sets the wagon provider to use to acquire and release wagon instances.
     * 
     * @param wagonProvider The wagon provider to use, may be {@code null}.
     * @return This factory for chaining, never {@code null}.
     */
    public WagonRepositoryConnectorFactory setWagonProvider( WagonProvider wagonProvider )
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
    public WagonRepositoryConnectorFactory setWagonConfigurator( WagonConfigurator wagonConfigurator )
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
    public WagonRepositoryConnectorFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    public RepositoryConnector newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        return new WagonRepositoryConnector( wagonProvider, wagonConfigurator, repository, session, fileProcessor,
                                             logger );
    }

}
