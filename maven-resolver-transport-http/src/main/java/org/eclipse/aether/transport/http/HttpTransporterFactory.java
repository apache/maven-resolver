package org.eclipse.aether.transport.http;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code http:} or {@code https:} protocol. The provided transporters
 * support uploads to WebDAV servers and resumable downloads.
 */
@Named( "http" )
public final class HttpTransporterFactory
    implements TransporterFactory
{
    private static final Map<String, ChecksumExtractor> EXTRACTORS;

    static
    {
        HashMap<String, ChecksumExtractor> map = new HashMap<>();
        map.put( Nexus2ChecksumExtractor.NAME, new Nexus2ChecksumExtractor() );
        map.put( XChecksumChecksumExtractor.NAME, new XChecksumChecksumExtractor() );
        EXTRACTORS = Collections.unmodifiableMap( map );
    }

    private float priority = 5.0f;

    private final Map<String, ChecksumExtractor> extractors;

    /**
     * Ctor for ServiceLocator.
     */
    @Deprecated
    public HttpTransporterFactory()
    {
        this( EXTRACTORS );
    }

    /**
     * Creates an (uninitialized) instance of this transporter factory. <em>Note:</em> In case of manual instantiation
     * by clients, the new factory needs to be configured via its various mutators before first use or runtime errors
     * will occur.
     */
    @Inject
    public HttpTransporterFactory( Map<String, ChecksumExtractor> extractors )
    {
        this.extractors = requireNonNull( extractors );
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
    public HttpTransporterFactory setPriority( float priority )
    {
        this.priority = priority;
        return this;
    }

    @Override
    public Transporter newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( repository, "repository cannot be null" );

        return new HttpTransporter( extractors, repository, session );
    }

}
