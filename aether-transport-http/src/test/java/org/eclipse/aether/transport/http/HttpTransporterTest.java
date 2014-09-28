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
package org.eclipse.aether.transport.http;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 */
public class HttpTransporterTest
{

    static
    {
        System.setProperty( "javax.net.ssl.trustStore",
                            new File( "src/test/resources/ssl/server-store" ).getAbsolutePath() );
        System.setProperty( "javax.net.ssl.trustStorePassword", "server-pwd" );
        System.setProperty( "javax.net.ssl.keyStore",
                            new File( "src/test/resources/ssl/client-store" ).getAbsolutePath() );
        System.setProperty( "javax.net.ssl.keyStorePassword", "client-pwd" );
    }

    private static final String REPO_ID = "test";

    @Rule
    public TestName testName = new TestName();

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private File repoDir;

    private HttpServer httpServer;

    private Authentication auth;

    private Proxy proxy;

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( REPO_ID, "default", url ).setAuthentication( auth ).setProxy( proxy ).build();
    }

    private void newTransporter( String url )
        throws Exception
    {
        if ( transporter != null )
        {
            transporter.close();
            transporter = null;
        }
        transporter = factory.newInstance( session, newRepo( url ) );
    }

    @Before
    public void setUp()
        throws Exception
    {
        System.out.println( "=== " + testName.getMethodName() + " ===" );
        session = TestUtils.newSession();
        factory = new HttpTransporterFactory( new TestLoggerFactory() );
        repoDir = TestFileUtils.createTempDir();
        TestFileUtils.writeString( new File( repoDir, "file.txt" ), "test" );
        TestFileUtils.writeString( new File( repoDir, "dir/file.txt" ), "test" );
        TestFileUtils.writeString( new File( repoDir, "empty.txt" ), "" );
        TestFileUtils.writeString( new File( repoDir, "some space.txt" ), "space" );
        File resumable = new File( repoDir, "resume.txt" );
        TestFileUtils.writeString( resumable, "resumable" );
        resumable.setLastModified( System.currentTimeMillis() - 90 * 1000 );
        httpServer = new HttpServer().setRepoDir( repoDir ).start();
        newTransporter( httpServer.getHttpUrl() );
    }

    @After
    public void tearDown()
        throws Exception
    {
        if ( transporter != null )
        {
            transporter.close();
            transporter = null;
        }
        if ( httpServer != null )
        {
            httpServer.stop();
            httpServer = null;
        }
        factory = null;
        session = null;
    }

    @Test
    public void testClassify()
    {
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new HttpResponseException( 403, "Forbidden" ) ) );
        assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( new HttpResponseException( 404, "Not Found" ) ) );
    }

    @Test
    public void testPeek()
        throws Exception
    {
        transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
    }

    @Test
    public void testPeek_NotFound()
        throws Exception
    {
        try
        {
            transporter.peek( new PeekTask( URI.create( "repo/missing.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 404, e.getStatusCode() );
            assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( e ) );
        }
    }

    @Test
    public void testPeek_Closed()
        throws Exception
    {
        transporter.close();
        try
        {
            transporter.peek( new PeekTask( URI.create( "repo/missing.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPeek_Authenticated()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
    }

    @Test
    public void testPeek_Unauthenticated()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        try
        {
            transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 401, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPeek_ProxyAuthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth );
        newTransporter( "http://bad.localhost:1/" );
        transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
    }

    @Test
    public void testPeek_ProxyUnauthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort() );
        newTransporter( "http://bad.localhost:1/" );
        try
        {
            transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 407, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPeek_SSL()
        throws Exception
    {
        httpServer.addSslConnector();
        newTransporter( httpServer.getHttpsUrl() );
        transporter.peek( new PeekTask( URI.create( "repo/file.txt" ) ) );
    }

    @Test
    public void testPeek_Redirect()
        throws Exception
    {
        httpServer.addSslConnector();
        transporter.peek( new PeekTask( URI.create( "redirect/file.txt" ) ) );
        transporter.peek( new PeekTask( URI.create( "redirect/file.txt?scheme=https" ) ) );
    }

    @Test
    public void testGet_ToMemory()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_ToFile()
        throws Exception
    {
        File file = TestFileUtils.createTempFile( "failure" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setDataFile( file ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", TestFileUtils.readString( file ) );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "test", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_EmptyResource()
        throws Exception
    {
        File file = TestFileUtils.createTempFile( "failure" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/empty.txt" ) ).setDataFile( file ).setListener( listener );
        transporter.get( task );
        assertEquals( "", TestFileUtils.readString( file ) );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 0, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
        assertEquals( "", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_EncodedResourcePath()
        throws Exception
    {
        GetTask task = new GetTask( URI.create( "repo/some%20space.txt" ) );
        transporter.get( task );
        assertEquals( "space", task.getDataString() );
    }

    @Test
    public void testGet_Authenticated()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_Unauthenticated()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        try
        {
            transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 401, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_ProxyAuthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        Authentication auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth );
        newTransporter( "http://bad.localhost:1/" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_ProxyUnauthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort() );
        newTransporter( "http://bad.localhost:1/" );
        try
        {
            transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 407, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_SSL()
        throws Exception
    {
        httpServer.addSslConnector();
        newTransporter( httpServer.getHttpsUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_WebDav()
        throws Exception
    {
        httpServer.setWebDav( HttpServer.WebDav.REQUIRED );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/dir/file.txt" ) ).setListener( listener );
        ( (HttpTransporter) transporter ).getState().setWebDav( true );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
        assertEquals( httpServer.getLogEntries().toString(), 1, httpServer.getLogEntries().size() );
    }

    @Test
    public void testGet_Redirect()
        throws Exception
    {
        httpServer.addSslConnector();
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "redirect/file.txt?scheme=https" ) ).setListener( listener );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( task.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_Resume()
        throws Exception
    {
        File file = TestFileUtils.createTempFile( "re" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/resume.txt" ) ).setDataFile( file, true ).setListener( listener );
        transporter.get( task );
        assertEquals( "resumable", TestFileUtils.readString( file ) );
        assertEquals( 1, listener.startedCount );
        assertEquals( 2, listener.dataOffset );
        assertEquals( 9, listener.dataLength );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "sumable", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_ResumeLocalContentsOutdated()
        throws Exception
    {
        File file = TestFileUtils.createTempFile( "re" );
        file.setLastModified( System.currentTimeMillis() - 5 * 60 * 1000 );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/resume.txt" ) ).setDataFile( file, true ).setListener( listener );
        transporter.get( task );
        assertEquals( "resumable", TestFileUtils.readString( file ) );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 9, listener.dataLength );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "resumable", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_ResumeRangesNotSupportedByServer()
        throws Exception
    {
        httpServer.setRangeSupport( false );
        File file = TestFileUtils.createTempFile( "re" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "repo/resume.txt" ) ).setDataFile( file, true ).setListener( listener );
        transporter.get( task );
        assertEquals( "resumable", TestFileUtils.readString( file ) );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 9, listener.dataLength );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "resumable", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_Checksums_Nexus()
        throws Exception
    {
        httpServer.setChecksumHeader( HttpServer.ChecksumHeader.NEXUS );
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
        assertEquals( "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", task.getChecksums().get( "SHA-1" ) );
    }

    @Test
    public void testGet_FileHandleLeak()
        throws Exception
    {
        for ( int i = 0; i < 100; i++ )
        {
            File file = TestFileUtils.createTempFile( "failure" );
            transporter.get( new GetTask( URI.create( "repo/file.txt" ) ).setDataFile( file ) );
            assertTrue( i + ", " + file.getAbsolutePath(), file.delete() );
        }
    }

    @Test
    public void testGet_NotFound()
        throws Exception
    {
        try
        {
            transporter.get( new GetTask( URI.create( "repo/missing.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 404, e.getStatusCode() );
            assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_Closed()
        throws Exception
    {
        transporter.close();
        try
        {
            transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_StartCancelled()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        try
        {
            transporter.get( task );
            fail( "Expected error" );
        }
        catch ( TransferCancelledException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
    }

    @Test
    public void testGet_ProgressCancelled()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        GetTask task = new GetTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        try
        {
            transporter.get( task );
            fail( "Expected error" );
        }
        catch ( TransferCancelledException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 1, listener.progressedCount );
    }

    @Test
    public void testPut_FromMemory()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_FromFile()
        throws Exception
    {
        File file = TestFileUtils.createTempFile( "upload" );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataFile( file );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_EmptyResource()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 0, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
        assertEquals( "", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_EncodedResourcePath()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
            new PutTask( URI.create( "repo/some%20space.txt" ) ).setListener( listener ).setDataString( "OK" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 2, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "OK", TestFileUtils.readString( new File( repoDir, "some space.txt" ) ) );
    }

    @Test
    public void testPut_Authenticated_ExpectContinue()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_Authenticated_ExpectContinueBroken()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        httpServer.setExpectSupport( HttpServer.ExpectContinue.BROKEN );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_Authenticated_ExpectContinueRejected()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        httpServer.setExpectSupport( HttpServer.ExpectContinue.FAIL );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_Authenticated_ExpectContinueRejected_ExplicitlyConfiguredHeader()
        throws Exception
    {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put( "Expect", "100-continue" );
        session.setConfigProperty( ConfigurationProperties.HTTP_HEADERS + ".test", headers );
        httpServer.setAuthentication( "testuser", "testpass" );
        httpServer.setExpectSupport( HttpServer.ExpectContinue.FAIL );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_Unauthenticated()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        try
        {
            transporter.put( task );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 401, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
    }

    @Test
    public void testPut_ProxyAuthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        Authentication auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort(), auth );
        newTransporter( "http://bad.localhost:1/" );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_ProxyUnauthenticated()
        throws Exception
    {
        httpServer.setProxyAuthentication( "testuser", "testpass" );
        proxy = new Proxy( Proxy.TYPE_HTTP, httpServer.getHost(), httpServer.getHttpPort() );
        newTransporter( "http://bad.localhost:1/" );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        try
        {
            transporter.put( task );
            fail( "Expected error" );
        }
        catch ( HttpResponseException e )
        {
            assertEquals( 407, e.getStatusCode() );
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
    }

    @Test
    public void testPut_SSL()
        throws Exception
    {
        httpServer.addSslConnector();
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpsUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_WebDav()
        throws Exception
    {
        httpServer.setWebDav( HttpServer.WebDav.REQUIRED );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
            new PutTask( URI.create( "repo/dir1/dir2/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "dir1/dir2/file.txt" ) ) );

        assertEquals( 5, httpServer.getLogEntries().size() );
        assertEquals( "OPTIONS", httpServer.getLogEntries().get( 0 ).method );
        assertEquals( "MKCOL", httpServer.getLogEntries().get( 1 ).method );
        assertEquals( "/repo/dir1/dir2/", httpServer.getLogEntries().get( 1 ).path );
        assertEquals( "MKCOL", httpServer.getLogEntries().get( 2 ).method );
        assertEquals( "/repo/dir1/", httpServer.getLogEntries().get( 2 ).path );
        assertEquals( "MKCOL", httpServer.getLogEntries().get( 3 ).method );
        assertEquals( "/repo/dir1/dir2/", httpServer.getLogEntries().get( 3 ).path );
        assertEquals( "PUT", httpServer.getLogEntries().get( 4 ).method );
    }

    @Test
    public void testPut_WebDavOptional_ManuallyDisabled()
        throws Exception
    {
        session.setConfigProperty( "aether.connector.http.webDav." + REPO_ID, "false" );
        httpServer.setWebDav( HttpServer.WebDav.OPTIONAL );
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
            new PutTask( URI.create( "repo/dir1/dir2/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "dir1/dir2/file.txt" ) ) );

        assertEquals( httpServer.getLogEntries().toString(), 3, httpServer.getLogEntries().size() );
        assertEquals( "OPTIONS", httpServer.getLogEntries().get( 0 ).method );
        assertEquals( "OPTIONS", httpServer.getLogEntries().get( 1 ).method );
        assertEquals( "PUT", httpServer.getLogEntries().get( 2 ).method );
    }

    @Test
    public void testPut_WebDavOptional_AutomaticallyDisabled_Artifactory()
        throws Exception
    {
        testPut_WebDavOptional_AutomaticallyDisabled( "Artifactory/3.3.0" );
    }

    @Test
    public void testPut_WebDavOptional_AutomaticallyDisabled_Nexus()
        throws Exception
    {
        testPut_WebDavOptional_AutomaticallyDisabled( "Nexus/2.9.0-02" );
    }

    private void testPut_WebDavOptional_AutomaticallyDisabled( String server )
        throws Exception
    {
        httpServer.setServer( server );
        httpServer.setWebDav( HttpServer.WebDav.OPTIONAL );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
            new PutTask( URI.create( "repo/dir1/dir2/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "dir1/dir2/file.txt" ) ) );

        assertEquals( httpServer.getLogEntries().toString(), 2, httpServer.getLogEntries().size() );
        assertEquals( "OPTIONS", httpServer.getLogEntries().get( 0 ).method );
        assertEquals( "PUT", httpServer.getLogEntries().get( 1 ).method );
    }

    @Test
    public void testPut_FileHandleLeak()
        throws Exception
    {
        for ( int i = 0; i < 100; i++ )
        {
            File src = TestFileUtils.createTempFile( "upload" );
            File dst = new File( repoDir, "file.txt" );
            transporter.put( new PutTask( URI.create( "repo/file.txt" ) ).setDataFile( src ) );
            assertTrue( i + ", " + src.getAbsolutePath(), src.delete() );
            assertTrue( i + ", " + dst.getAbsolutePath(), dst.delete() );
        }
    }

    @Test
    public void testPut_Closed()
        throws Exception
    {
        transporter.close();
        try
        {
            transporter.put( new PutTask( URI.create( "repo/missing.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPut_StartCancelled()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelStart = true;
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        try
        {
            transporter.put( task );
            fail( "Expected error" );
        }
        catch ( TransferCancelledException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
    }

    @Test
    public void testPut_ProgressCancelled()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        try
        {
            transporter.put( task );
            fail( "Expected error" );
        }
        catch ( TransferCancelledException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 1, listener.progressedCount );
    }

    @Test
    public void testGetPut_AuthCache()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        GetTask get = new GetTask( URI.create( "repo/file.txt" ) );
        transporter.get( get );
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "repo/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 1, listener.startedCount );
    }

    @Test( timeout = 20000 )
    public void testConcurrency()
        throws Exception
    {
        httpServer.setAuthentication( "testuser", "testpass" );
        auth = new AuthenticationBuilder().addUsername( "testuser" ).addPassword( "testpass" ).build();
        newTransporter( httpServer.getHttpUrl() );
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread threads[] = new Thread[20];
        for ( int i = 0; i < threads.length; i++ )
        {
            final String path = "repo/file.txt?i=" + i;
            threads[i] = new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        for ( int j = 0; j < 100; j++ )
                        {
                            GetTask task = new GetTask( URI.create( path ) );
                            transporter.get( task );
                            assertEquals( "test", task.getDataString() );
                        }
                    }
                    catch ( Throwable t )
                    {
                        error.compareAndSet( null, t );
                        System.err.println( path );
                        t.printStackTrace();
                    }
                }
            };
            threads[i].setName( "Task-" + i );
        }
        for ( Thread thread : threads )
        {
            thread.start();
        }
        for ( Thread thread : threads )
        {
            thread.join();
        }
        assertNull( String.valueOf( error.get() ), error.get() );
    }

    @Test( timeout = 1000 )
    public void testConnectTimeout()
        throws Exception
    {
        session.setConfigProperty( ConfigurationProperties.CONNECT_TIMEOUT, 100 );
        int port = 1;
        newTransporter( "http://localhost:" + port );
        try
        {
            transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
            fail( "Expected error" );
        }
        catch ( ConnectTimeoutException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
        catch ( HttpHostConnectException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test( timeout = 1000 )
    public void testRequestTimeout()
        throws Exception
    {
        session.setConfigProperty( ConfigurationProperties.REQUEST_TIMEOUT, 100 );
        ServerSocket server = new ServerSocket( 0 );
        newTransporter( "http://localhost:" + server.getLocalPort() );
        try
        {
            try
            {
                transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
                fail( "Expected error" );
            }
            catch ( SocketTimeoutException e )
            {
                assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
            }
        }
        finally
        {
            server.close();
        }
    }

    @Test
    public void testUserAgent()
        throws Exception
    {
        session.setConfigProperty( ConfigurationProperties.USER_AGENT, "SomeTest/1.0" );
        newTransporter( httpServer.getHttpUrl() );
        transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
        assertEquals( 1, httpServer.getLogEntries().size() );
        for ( HttpServer.LogEntry log : httpServer.getLogEntries() )
        {
            assertEquals( "SomeTest/1.0", log.headers.get( "User-Agent" ) );
        }
    }

    @Test
    public void testCustomHeaders()
        throws Exception
    {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put( "User-Agent", "Custom/1.0" );
        headers.put( "X-CustomHeader", "Custom-Value" );
        session.setConfigProperty( ConfigurationProperties.USER_AGENT, "SomeTest/1.0" );
        session.setConfigProperty( ConfigurationProperties.HTTP_HEADERS + ".test", headers );
        newTransporter( httpServer.getHttpUrl() );
        transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
        assertEquals( 1, httpServer.getLogEntries().size() );
        for ( HttpServer.LogEntry log : httpServer.getLogEntries() )
        {
            for ( Map.Entry<String, String> entry : headers.entrySet() )
            {
                assertEquals( entry.getKey(), entry.getValue(), log.headers.get( entry.getKey() ) );
            }
        }
    }

    @Test
    public void testCredentialEncoding_Default()
        throws Exception
    {
        testCredentialEncoding( null );
    }

    @Test
    public void testCredentialEncoding_Utf8()
        throws Exception
    {
        testCredentialEncoding( "UTF-8" );
    }

    private void testCredentialEncoding( String encoding )
        throws Exception
    {
        session.setConfigProperty( ConfigurationProperties.HTTP_CREDENTIAL_ENCODING, encoding );
        String username = "testuser", password = "\u00e4\u00f6\u00fc";
        httpServer.setAuthentication( username, password );
        httpServer.setCredentialEncoding( encoding );
        auth = new AuthenticationBuilder().addUsername( username ).addPassword( password ).build();
        newTransporter( httpServer.getHttpUrl() );
        transporter.get( new GetTask( URI.create( "repo/file.txt" ) ) );
    }

    @Test
    public void testServerAuthScope_FollowsSslRedirect()
        throws Exception
    {
        String username = "testuser", password = "testpass";
        httpServer.setAuthentication( username, password );
        httpServer.addSslConnector();
        auth = new AuthenticationBuilder().addUsername( username ).addPassword( password ).build();
        newTransporter( httpServer.getHttpUrl() );
        transporter.get( new GetTask( URI.create( "redirect/file.txt?scheme=https" ) ) );
    }

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
            ( (ConnPoolControl<?>) ( (HttpTransporter) transporter ).getState().getConnectionManager() ).getTotalStats();
        assertEquals( stats.toString(), 1, stats.getAvailable() );
    }

    @Test( expected = NoTransporterException.class )
    public void testInit_BadProtocol()
        throws Exception
    {
        newTransporter( "bad:/void" );
    }

    @Test( expected = NoTransporterException.class )
    public void testInit_BadUrl()
        throws Exception
    {
        newTransporter( "http://localhost:NaN" );
    }

    @Test
    public void testInit_CaseInsensitiveProtocol()
        throws Exception
    {
        newTransporter( "http://localhost" );
        newTransporter( "HTTP://localhost" );
        newTransporter( "Http://localhost" );
        newTransporter( "https://localhost" );
        newTransporter( "HTTPS://localhost" );
        newTransporter( "HttpS://localhost" );
    }

}
