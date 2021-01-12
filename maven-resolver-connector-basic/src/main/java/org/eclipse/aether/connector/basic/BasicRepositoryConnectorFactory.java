package org.eclipse.aether.connector.basic;

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

import javax.inject.Inject;
import javax.inject.Named;

import static java.util.Objects.requireNonNull;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * A repository connector factory that employs pluggable
 * {@link org.eclipse.aether.spi.connector.transport.TransporterFactory transporters} and
 * {@link org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory repository layouts} for the transfers.
 */
@Named( "basic" )
public final class BasicRepositoryConnectorFactory
    implements RepositoryConnectorFactory
{
    private final TransporterProvider transporterProvider;

    private final RepositoryLayoutProvider layoutProvider;

    private final ChecksumPolicyProvider checksumPolicyProvider;

    private final FileProcessor fileProcessor;

    private float priority;

    @Inject
    public BasicRepositoryConnectorFactory( TransporterProvider transporterProvider,
                                            RepositoryLayoutProvider layoutProvider,
                                            ChecksumPolicyProvider checksumPolicyProvider,
                                            FileProcessor fileProcessor )
    {
        this.transporterProvider = requireNonNull(
            transporterProvider, "transporter provider cannot be null" );
        this.layoutProvider =  requireNonNull(
            layoutProvider, "repository layout provider cannot be null" );
        this.checksumPolicyProvider = requireNonNull(
            checksumPolicyProvider, "checksum policy provider cannot be null" );
        this.fileProcessor = requireNonNull(
            fileProcessor, "file processor cannot be null" );
    }

    @Override
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

    @Override
    public RepositoryConnector newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryConnectorException
    {
        return new BasicRepositoryConnector( session, repository, transporterProvider, layoutProvider,
                                             checksumPolicyProvider, fileProcessor );
    }

}
