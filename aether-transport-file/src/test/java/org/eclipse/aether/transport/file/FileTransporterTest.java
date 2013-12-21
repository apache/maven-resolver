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
package org.eclipse.aether.transport.file;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class FileTransporterTest
{

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private File repoDir;

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( "test", "default", url ).build();
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
        session = TestUtils.newSession();
        factory = new FileTransporterFactory( new TestLoggerFactory() );
        repoDir = TestFileUtils.createTempDir();
        TestFileUtils.writeString( new File( repoDir, "file.txt" ), "test" );
        TestFileUtils.writeString( new File( repoDir, "empty.txt" ), "" );
        TestFileUtils.writeString( new File( repoDir, "some space.txt" ), "space" );
        newTransporter( repoDir.toURI().toString() );
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
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( new ResourceNotFoundException( "test" ) ) );
    }

    @Test
    public void testPeek()
        throws Exception
    {
        transporter.peek( new PeekTask( URI.create( "file.txt" ) ) );
    }

    @Test
    public void testPeek_NotFound()
        throws Exception
    {
        try
        {
            transporter.peek( new PeekTask( URI.create( "missing.txt" ) ) );
            fail( "Expected error" );
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
        transporter.close();
        try
        {
            transporter.peek( new PeekTask( URI.create( "missing.txt" ) ) );
            fail( "Expected error" );
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
        RecordingTransportListener listener = new RecordingTransportListener();
        GetTask task = new GetTask( URI.create( "file.txt" ) ).setListener( listener );
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
        GetTask task = new GetTask( URI.create( "file.txt" ) ).setDataFile( file ).setListener( listener );
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
        GetTask task = new GetTask( URI.create( "empty.txt" ) ).setDataFile( file ).setListener( listener );
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
        GetTask task = new GetTask( URI.create( "some%20space.txt" ) );
        transporter.get( task );
        assertEquals( "space", task.getDataString() );
    }

    @Test
    public void testGet_Fragment()
        throws Exception
    {
        GetTask task = new GetTask( URI.create( "file.txt#ignored" ) );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
    }

    @Test
    public void testGet_Query()
        throws Exception
    {
        GetTask task = new GetTask( URI.create( "file.txt?ignored" ) );
        transporter.get( task );
        assertEquals( "test", task.getDataString() );
    }

    @Test
    public void testGet_FileHandleLeak()
        throws Exception
    {
        for ( int i = 0; i < 100; i++ )
        {
            File file = TestFileUtils.createTempFile( "failure" );
            transporter.get( new GetTask( URI.create( "file.txt" ) ).setDataFile( file ) );
            assertTrue( i + ", " + file.getAbsolutePath(), file.delete() );
        }
    }

    @Test
    public void testGet_NotFound()
        throws Exception
    {
        try
        {
            transporter.get( new GetTask( URI.create( "missing.txt" ) ) );
            fail( "Expected error" );
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
        transporter.close();
        try
        {
            transporter.get( new GetTask( URI.create( "file.txt" ) ) );
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
        GetTask task = new GetTask( URI.create( "file.txt" ) ).setListener( listener );
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
        GetTask task = new GetTask( URI.create( "file.txt" ) ).setListener( listener );
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
        PutTask task = new PutTask( URI.create( "file.txt" ) ).setListener( listener ).setDataString( "upload" );
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
        PutTask task = new PutTask( URI.create( "file.txt" ) ).setListener( listener ).setDataFile( file );
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
        PutTask task = new PutTask( URI.create( "file.txt" ) ).setListener( listener );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 0, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertEquals( 0, listener.progressedCount );
        assertEquals( "", TestFileUtils.readString( new File( repoDir, "file.txt" ) ) );
    }

    @Test
    public void testPut_NonExistentParentDir()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task =
            new PutTask( URI.create( "dir/sub/dir/file.txt" ) ).setListener( listener ).setDataString( "upload" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 6, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "upload", TestFileUtils.readString( new File( repoDir, "dir/sub/dir/file.txt" ) ) );
    }

    @Test
    public void testPut_EncodedResourcePath()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        PutTask task = new PutTask( URI.create( "some%20space.txt" ) ).setListener( listener ).setDataString( "OK" );
        transporter.put( task );
        assertEquals( 0, listener.dataOffset );
        assertEquals( 2, listener.dataLength );
        assertEquals( 1, listener.startedCount );
        assertTrue( "Count: " + listener.progressedCount, listener.progressedCount > 0 );
        assertEquals( "OK", TestFileUtils.readString( new File( repoDir, "some space.txt" ) ) );
    }

    @Test
    public void testPut_FileHandleLeak()
        throws Exception
    {
        for ( int i = 0; i < 100; i++ )
        {
            File src = TestFileUtils.createTempFile( "upload" );
            File dst = new File( repoDir, "file.txt" );
            transporter.put( new PutTask( URI.create( "file.txt" ) ).setDataFile( src ) );
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
            transporter.put( new PutTask( URI.create( "missing.txt" ) ) );
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
        PutTask task = new PutTask( URI.create( "file.txt" ) ).setListener( listener ).setDataString( "upload" );
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
        assertFalse( new File( repoDir, "file.txt" ).exists() );
    }

    @Test
    public void testPut_ProgressCancelled()
        throws Exception
    {
        RecordingTransportListener listener = new RecordingTransportListener();
        listener.cancelProgress = true;
        PutTask task = new PutTask( URI.create( "file.txt" ) ).setListener( listener ).setDataString( "upload" );
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
        assertFalse( new File( repoDir, "file.txt" ).exists() );
    }

    @Test( expected = NoTransporterException.class )
    public void testInit_BadProtocol()
        throws Exception
    {
        newTransporter( "bad:/void" );
    }

    @Test
    public void testInit_CaseInsensitiveProtocol()
        throws Exception
    {
        newTransporter( "file:/void" );
        newTransporter( "FILE:/void" );
        newTransporter( "File:/void" );
    }

    @Test
    public void testInit_OpaqueUrl()
        throws Exception
    {
        testInit( "file:repository", "repository" );
    }

    @Test
    public void testInit_OpaqueUrlTrailingSlash()
        throws Exception
    {
        testInit( "file:repository/", "repository" );
    }

    @Test
    public void testInit_OpaqueUrlSpaces()
        throws Exception
    {
        testInit( "file:repo%20space", "repo space" );
    }

    @Test
    public void testInit_OpaqueUrlSpacesDecoded()
        throws Exception
    {
        testInit( "file:repo space", "repo space" );
    }

    @Test
    public void testInit_HierarchicalUrl()
        throws Exception
    {
        testInit( "file:/repository", "/repository" );
    }

    @Test
    public void testInit_HierarchicalUrlTrailingSlash()
        throws Exception
    {
        testInit( "file:/repository/", "/repository" );
    }

    @Test
    public void testInit_HierarchicalUrlSpaces()
        throws Exception
    {
        testInit( "file:/repo%20space", "/repo space" );
    }

    @Test
    public void testInit_HierarchicalUrlSpacesDecoded()
        throws Exception
    {
        testInit( "file:/repo space", "/repo space" );
    }

    @Test
    public void testInit_HierarchicalUrlRoot()
        throws Exception
    {
        testInit( "file:/", "/" );
    }

    @Test
    public void testInit_HierarchicalUrlHostNoPath()
        throws Exception
    {
        testInit( "file://host/", "/" );
    }

    @Test
    public void testInit_HierarchicalUrlHostPath()
        throws Exception
    {
        testInit( "file://host/dir", "/dir" );
    }

    private void testInit( String base, String expected )
        throws Exception
    {
        newTransporter( base );
        File exp = new File( expected ).getAbsoluteFile();
        assertEquals( exp, ( (FileTransporter) transporter ).getBasedir() );
    }

}
