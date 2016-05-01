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
package org.eclipse.aether.connector.basic;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * A repository connector factory that employs pluggable
 * {@link org.eclipse.aether.spi.connector.transport.TransporterFactory transporters} and
 * {@link org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory repository layouts} for the transfers.
 */
@Named( "basic" )
public final class BasicRepositoryConnectorFactory
    implements RepositoryConnectorFactory, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private TransporterProvider transporterProvider;

    private RepositoryLayoutProvider layoutProvider;

    private ChecksumPolicyProvider checksumPolicyProvider;

    private FileProcessor fileProcessor;

    private float priority;

    /**
     * Creates an (uninitialized) instance of this connector factory. <em>Note:</em> In case of manual instantiation by
     * clients, the new factory needs to be configured via its various mutators before first use or runtime errors will
     * occur.
     */
    public BasicRepositoryConnectorFactory()
    {
        // enables default constructor
    }

    @Inject
    BasicRepositoryConnectorFactory( TransporterProvider transporterProvider, RepositoryLayoutProvider layoutProvider,
                                     ChecksumPolicyProvider checksumPolicyProvider, FileProcessor fileProcessor,
                                     LoggerFactory loggerFactory )
    {
        setTransporterProvider( transporterProvider );
        setRepositoryLayoutProvider( layoutProvider );
        setChecksumPolicyProvider( checksumPolicyProvider );
        setFileProcessor( fileProcessor );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setTransporterProvider( locator.getService( TransporterProvider.class ) );
        setRepositoryLayoutProvider( locator.getService( RepositoryLayoutProvider.class ) );
        setChecksumPolicyProvider( locator.getService( ChecksumPolicyProvider.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
    }

    /**
     * Sets the logger factory to use for this component.
     * 
     * @param loggerFactory The logger factory to use, may be {@code null} to disable logging.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, BasicRepositoryConnector.class );
        return this;
    }

    /**
     * Sets the transporter provider to use for this component.
     * 
     * @param transporterProvider The transporter provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setTransporterProvider( TransporterProvider transporterProvider )
    {
        if ( transporterProvider == null )
        {
            throw new IllegalArgumentException( "transporter provider has not been specified" );
        }
        this.transporterProvider = transporterProvider;
        return this;
    }

    /**
     * Sets the repository layout provider to use for this component.
     * 
     * @param layoutProvider The repository layout provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setRepositoryLayoutProvider( RepositoryLayoutProvider layoutProvider )
    {
        if ( layoutProvider == null )
        {
            throw new IllegalArgumentException( "repository layout provider has not been specified" );
        }
        this.layoutProvider = layoutProvider;
        return this;
    }

    /**
     * Sets the checksum policy provider to use for this component.
     * 
     * @param checksumPolicyProvider The checksum policy provider to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setChecksumPolicyProvider( ChecksumPolicyProvider checksumPolicyProvider )
    {
        if ( checksumPolicyProvider == null )
        {
            throw new IllegalArgumentException( "checksum policy provider has not been specified" );
        }
        this.checksumPolicyProvider = checksumPolicyProvider;
        return this;
    }

    /**
     * Sets the file processor to use for this component.
     * 
     * @param fileProcessor The file processor to use, must not be {@code null}.
     * @return This component for chaining, never {@code null}.
     */
    public BasicRepositoryConnectorFactory setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
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
    public BasicRepositoryConnectorFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    public RepositoryConnector newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        return new BasicRepositoryConnector( session, repository, transporterProvider, layoutProvider,
                                             checksumPolicyProvider, fileProcessor, logger );
    }

}
