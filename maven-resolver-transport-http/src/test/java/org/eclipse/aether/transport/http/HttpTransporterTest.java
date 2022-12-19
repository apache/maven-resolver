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

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.HttpResponseException;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.http.HttpServer;
import org.eclipse.aether.internal.test.http.HttpTransporterTestSupport;
import org.eclipse.aether.internal.test.http.RecordingTransportListener;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class HttpTransporterTest extends HttpTransporterTestSupport
{
    @Override
    protected TransporterFactory newTransporterFactory( RepositorySystemSession session )
    {
        return new HttpTransporterFactory();
    }

    @Override
    protected boolean isWebDAVSupported()
    {
        return true;
    }

    @Override
    protected boolean enableWebDavSupport( Transporter transporter )
    {
        ( (HttpTransporter) transporter ).getState().setWebDav( true );
        return true;
    }

    @Test
    public void testClassify()
    {
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new HttpResponseException( 403, "Forbidden" ) ) );
        assertEquals( Transporter.ERROR_NOT_FOUND,
                transporter.classify( new HttpResponseException( 404, "Not Found" ) ) );
    }

    /**
     * HttpClient specific test: ensures that auth scheme state is reused (see {@link LocalState} and
     * {@link GlobalState}), as this transport implementation does quite fine-grained control of the HTTP client and
     * the HTTP transactions.
     */
    @Test
    public void testAuthSchemeReuse()
            throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        httpServer.setProxyAuthentication( "proxyuser", "proxypass" );
        session.setCache( new DefaultRepositoryCache() );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        Authentication auth = new AuthenticationBuilder().addUsername( "proxyuser" ).addPassword( "proxypass" ).build();
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth );
        newTransporter( "http://bad.localhost:1/" );
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 3, httpServer.getLogEntries().size() );
        httpServer.getLogEntries().clear();
        newTransporter( "http://bad.localhost:1/" );
        task = new GetTask( URI.create( "repo/file.txt" ) );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 1, httpServer.getLogEntries().size() );
        assertNotNull( httpServer.getLogEntries().get( 0 ).headers.get( "Authorization" ) );
        assertNotNull( httpServer.getLogEntries().get( 0 ).headers.get( "Proxy-Authorization" ) );
    }

    /**
     * HttpClient specific tests: no other transport client overrides headers set by user, but in this test two
     * "hardly justified things" make this work, but only with HttpClient transport. For start, unsure why would
     * a user set EXPECT header as configuration, while HttpClient goes into game and removes it as needed. Many
     * other clients (notably Java11 or Jetty) simply does not allow EXPECT as "custom header" at all or, as expected
     * by protocol, returns HTTP 417 Expectation Failed.
     */
    @Test
    public void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader()
            throws Exception
    {
        Map<String, String> headers = new HashMap<>();
        headers.put( "Expect", "100-continue" );
        session.setConfigProperty( ConfigurationProperties.HTTP_HEADERS + ".test", headers );
        httpServer.setAuthentication( "testuser", "testpass" );
        httpServer.setExpectSupport( HttpServer.ExpectContinue.FAIL );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0L, listener.dataOffset );
        assertEquals( 6L, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    /**
     * HttpClient specific test: uses HttpClient internal API to assert that HTTP/1.1 connections are pooled.
     */
    @Test
    public void testConnectionReuse()
            throws Exception
    {
        httpServer.addSslConnector();
        session.setCache( new DefaultRepositoryCache() );
        for ( int i = 0; i < 3; i++ )
        {
            newTransporter( httpServer.getHttpsUrl() );
            GetTask task = new GetTask( URI.create( "repo/file.txt" ) );
            transporter.get( task );
            assertEquals( "test", task.getDataString() );
        }
        PoolStats stats =
                ( (ConnPoolControl<?>) ( (HttpTransporter) transporter ).getState()
                        .getConnectionManager() ).getTotalStats();
        assertEquals( stats.toString(), 1, stats.getAvailable() );
    }
}
