package org.eclipse.aether.transport.jetty;

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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.StringDigestUtil;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.client.http.HttpClientConnectionFactory;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.ClientConnectionFactoryOverHTTP2;
import org.eclipse.jetty.http3.client.HTTP3Client;
import org.eclipse.jetty.http3.client.http.ClientConnectionFactoryOverHTTP3;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnector;

import static java.util.Objects.requireNonNull;

/**
 * A transporter factory for repositories using the {@code http:} or {@code https:} protocol. The provided transporters
 * support uploads to WebDAV servers and resumable downloads.
 */
@Named( "jetty" )
public final class JettyTransporterFactory
        implements TransporterFactory
{
    private static final String CLIENT_KEY_PREFIX = JettyTransporterFactory.class.getName() + ".client.";

    private static Map<String, ChecksumExtractor> getManuallyCreatedExtractors()
    {
        HashMap<String, ChecksumExtractor> map = new HashMap<>();
        map.put( Nexus2ChecksumExtractor.NAME, new Nexus2ChecksumExtractor() );
        map.put( XChecksumChecksumExtractor.NAME, new XChecksumChecksumExtractor() );
        return Collections.unmodifiableMap( map );
    }

    private float priority = 7.0f;

    private final Map<String, ChecksumExtractor> extractors;

    /**
     * Ctor for ServiceLocator.
     */
    @Deprecated
    public JettyTransporterFactory()
    {
        this( getManuallyCreatedExtractors() );
    }

    /**
     * Creates an (uninitialized) instance of this transporter factory. <em>Note:</em> In case of manual instantiation
     * by clients, the new factory needs to be configured via its various mutators before first use or runtime errors
     * will occur.
     */
    @Inject
    public JettyTransporterFactory( Map<String, ChecksumExtractor> extractors )
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
    public JettyTransporterFactory setPriority( float priority )
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

        if ( !"http".equalsIgnoreCase( repository.getProtocol() )
                && !"https".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }

        final String clientKey = CLIENT_KEY_PREFIX
                + StringDigestUtil.sha1( repository.getId() + repository.getUrl() );

        try
        {
            HttpClient httpClient = (HttpClient) session.getData().computeIfAbsent( clientKey, () ->
            {
                try
                {
                    String userAgent = ConfigUtils.getString( session,
                            ConfigurationProperties.DEFAULT_USER_AGENT,
                            ConfigurationProperties.USER_AGENT );

                    int connectTimeout = ConfigUtils.getInteger( session,
                            ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                            ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                            ConfigurationProperties.CONNECT_TIMEOUT );

                    ClientConnector connector = new ClientConnector();
                    ClientConnectionFactory.Info http1 = HttpClientConnectionFactory.HTTP11;
                    HTTP2Client http2Client = new HTTP2Client( connector );
                    ClientConnectionFactoryOverHTTP2.HTTP2 http2 =
                            new ClientConnectionFactoryOverHTTP2.HTTP2( http2Client );
                    HTTP3Client h3Client = new HTTP3Client();
                    ClientConnectionFactoryOverHTTP3.HTTP3 http3 =
                            new ClientConnectionFactoryOverHTTP3.HTTP3( h3Client );
                    HttpClientTransportDynamic transport =
                            new HttpClientTransportDynamic( connector, http3, http2, http1 );

                    HttpClient client = new HttpClient( transport );
                    client.setConnectTimeout( connectTimeout );
                    client.setUserAgentField( new HttpField( HttpHeader.USER_AGENT, userAgent ) );
                    client.setFollowRedirects( false );

                    client.start();

                    return client;
                }
                catch ( Exception e )
                {
                    throw new IllegalStateException( e );
                }
            } );

            return new JettyTransporter( extractors, httpClient, repository, session );
        }
        catch ( IllegalStateException e )
        {
            throw new NoTransporterException( repository, e );
        }

        // TODO: cleanly stop clients
    }
}
