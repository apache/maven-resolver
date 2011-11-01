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
package org.eclipse.aether.connector.async;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.eclipse.aether.internal.test.impl.SysoutLogger;
import org.eclipse.aether.internal.test.impl.TestFileProcessor;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.impl.StubArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 */
public class ResumeGetTest
{

    private AsyncRepositoryConnectorFactory factory;

    private TestRepositorySystemSession session;

    private Artifact artifact;

    private Server server;

    // NOTE: Length of pattern should not be divisable by 2 to catch data continuation errors during resume
    private static final int[] CONTENT_PATTERN = { 'a', 'B', ' ' };

    @Before
    public void before()
        throws Exception
    {
        factory = new AsyncRepositoryConnectorFactory( new TestFileProcessor() );
        factory.setLogger( new SysoutLogger() );
        session = new TestRepositorySystemSession();
        artifact = new StubArtifact( "gid", "aid", "classifier", "extension", "version" );
        server = new Server( 0 );
    }

    @After
    public void after()
        throws Exception
    {
        if ( server != null )
        {
            server.stop();
        }

        factory = null;
        session = null;
        server = null;

        TestFileUtils.deleteTempFiles();
    }

    private String url()
    {
        return "http://localhost:" + server.getConnectors()[0].getLocalPort() + "/";
    }

    private void assertContentPattern( File file )
        throws IOException
    {
        byte[] content = TestFileUtils.getContent( file );
        for ( int i = 0; i < content.length; i++ )
        {
            assertEquals( file.getAbsolutePath() + " corrupted at offset " + i, CONTENT_PATTERN[i
                % CONTENT_PATTERN.length], content[i] );
        }
    }

    @Test
    public void testResumeInterruptedDownloadUsingRangeRequests()
        throws Exception
    {
        FlakyHandler flakyHandler = new FlakyHandler( 4 );
        server.setHandler( flakyHandler );
        server.start();

        File file = TestFileUtils.createTempFile( "" );
        file.delete();

        ArtifactDownload download = new ArtifactDownload( artifact, "", file, RepositoryPolicy.CHECKSUM_POLICY_IGNORE );

        RemoteRepository repo = new RemoteRepository( "test", "default", url() );
        RepositoryConnector connector = factory.newInstance( session, repo );
        try
        {
            connector.get( Arrays.asList( download ), null );
        }
        finally
        {
            connector.close();
        }

        assertNull( String.valueOf( download.getException() ), download.getException() );
        assertTrue( "Missing " + file.getAbsolutePath(), file.isFile() );
        assertEquals( "Bad size of " + file.getAbsolutePath(), flakyHandler.totalSize, file.length() );
        assertContentPattern( file );
    }

    private static class FlakyHandler
        extends AbstractHandler
    {

        private static final Pattern RANGE = Pattern.compile( "bytes=([0-9]+)-" );

        private final int requiredRequests;

        private final Map<String, Integer> madeRequests;

        private final int totalSize;

        private final int chunkSize;

        public FlakyHandler( int requiredRequests )
        {
            this.requiredRequests = requiredRequests;
            madeRequests = new ConcurrentHashMap<String, Integer>();

            totalSize = 1024 * 128;
            chunkSize = ( requiredRequests > 1 ) ? totalSize / ( requiredRequests - 1 ) - 1 : totalSize;
        }

        public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            Integer attempts = madeRequests.get( target );
            attempts = ( attempts == null ) ? Integer.valueOf( 1 ) : Integer.valueOf( attempts.intValue() + 1 );
            madeRequests.put( target, attempts );

            if ( attempts.intValue() > requiredRequests )
            {
                response.setStatus( HttpURLConnection.HTTP_BAD_REQUEST );
                response.flushBuffer();
                return;
            }

            int lb = 0, ub = totalSize - 1;

            String range = request.getHeader( "Range" );
            if ( range != null && range.matches( RANGE.pattern() ) )
            {
                Matcher m = RANGE.matcher( range );
                m.matches();
                lb = Integer.parseInt( m.group( 1 ) );
            }

            response.setStatus( ( lb > 0 ) ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK );
            response.setContentLength( totalSize - lb );
            response.setContentType( "Content-type: text/plain; charset=UTF-8" );
            if ( lb > 0 )
            {
                response.setHeader( "Content-Range", "bytes " + lb + "-" + ub + "/" + totalSize );
            }
            response.flushBuffer();

            OutputStream out = response.getOutputStream();

            for ( int i = lb, j = 0; i <= ub; i++, j++ )
            {
                if ( j >= chunkSize )
                {
                    out.flush();

                    throw new IOException( "oups, we're dead" );
                }

                out.write( CONTENT_PATTERN[i % CONTENT_PATTERN.length] );
            }

            out.close();
        }

    }

}
