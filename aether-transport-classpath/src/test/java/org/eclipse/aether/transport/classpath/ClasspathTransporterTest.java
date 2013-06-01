/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.classpath;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetRequest;
import org.eclipse.aether.spi.connector.transport.PeekRequest;
import org.eclipse.aether.spi.connector.transport.PutRequest;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class ClasspathTransporterTest
{

    private DefaultRepositorySystemSession session;

    private ClasspathTransporterFactory factory;

    private Transporter transporter;

    private RemoteRepository newRepo( String base )
    {
        return new RemoteRepository.Builder( "test", "default", "classpath:/" + base ).build();
    }

    private void newTransporter( String repoBase )
        throws Exception
    {
        if ( transporter != null )
        {
            transporter.close();
        }
        transporter = factory.newInstance( session, newRepo( repoBase ) );
    }

    @Before
    public void setUp()
    {
        session = TestUtils.newSession();
        factory = new ClasspathTransporterFactory( new TestLoggerFactory() );
    }

    @After
    public void tearDown()
    {
        if ( transporter != null )
        {
            transporter.close();
            transporter = null;
        }
        factory = null;
        session = null;
    }

    @Test
    public void testClassify()
        throws Exception
    {
        newTransporter( "test" );
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( new ResourceNotFoundException( "test" ) ) );
    }

    @Test
    public void testPeek()
        throws Exception
    {
        newTransporter( "repository/a" );
        transporter.peek( new PeekRequest( URI.create( "file.txt" ) ) );
    }

    @Test
    public void testPeek_NotFound()
        throws Exception
    {
        newTransporter( "repository/a" );
        try
        {
            transporter.peek( new PeekRequest( URI.create( "missing.txt" ) ) );
        }
        catch ( ResourceNotFoundException e )
        {
            assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( e ) );
        }
    }

    @Test
    public void testPeek_Closed()
        throws Exception
    {
        newTransporter( "repository/a" );
        transporter.close();
        try
        {
            transporter.peek( new PeekRequest( URI.create( "missing.txt" ) ) );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_ToMemory()
        throws Exception
    {
        newTransporter( "repository/a" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetRequest request = new GetRequest( URI.create( "file.txt" ) ).setListener( listener );
        transporter.get( request );
        assertEquals( "test", request.getDataString() );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( request.getDataString(), listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_ToFile()
        throws Exception
    {
        newTransporter( "repository/a" );
        File file = TestFileUtils.createTempFile( "failure" );
        RecordingTransportListener listener = new RecordingTransportListener();
        GetRequest request = new GetRequest( URI.create( "file.txt" ) ).setDataFile( file ).setListener( listener );
        transporter.get( request );
        assertEquals( "test", TestFileUtils.readString( file ) );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 4, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "test", listener.baos.toString( "UTF-8" ) );
    }

    @Test
    public void testGet_NotFound()
        throws Exception
    {
        newTransporter( "repository/a" );
        try
        {
            transporter.get( new GetRequest( URI.create( "missing.txt" ) ) );
        }
        catch ( ResourceNotFoundException e )
        {
            assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( e ) );
        }
    }

    @Test
    public void testGet_Closed()
        throws Exception
    {
        newTransporter( "repository/a" );
        transporter.close();
        try
        {
            transporter.get( new GetRequest( URI.create( "file.txt" ) ) );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPut()
        throws Exception
    {
        newTransporter( "repository/a" );
        try
        {
            transporter.put( new PutRequest( URI.create( "missing.txt" ) ) );
        }
        catch ( UnsupportedOperationException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

    @Test
    public void testPut_Closed()
        throws Exception
    {
        newTransporter( "repository/a" );
        transporter.close();
        try
        {
            transporter.put( new PutRequest( URI.create( "missing.txt" ) ) );
        }
        catch ( IllegalStateException e )
        {
            assertEquals( Transporter.ERROR_OTHER, transporter.classify( e ) );
        }
    }

}
