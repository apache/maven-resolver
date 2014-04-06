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
package org.eclipse.aether.connector.basic;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PartialFileTest
{

    private static class StubRemoteAccessChecker
        implements PartialFile.RemoteAccessChecker
    {

        Exception exception;

        int invocations;

        public void checkRemoteAccess()
            throws Exception
        {
            invocations++;
            if ( exception != null )
            {
                throw exception;
            }
        }

    }

    private static class ConcurrentWriter
        extends Thread
    {

        private final File dstFile;

        private final File partFile;

        private final File lockFile;

        private final CountDownLatch locked;

        private final int sleep;

        volatile int length;

        Exception error;

        public ConcurrentWriter( File dstFile, int sleep, int length )
            throws InterruptedException
        {
            super( "ConcurrentWriter-" + dstFile.getAbsolutePath() );
            this.dstFile = dstFile;
            partFile = new File( dstFile.getPath() + PartialFile.EXT_PART );
            lockFile = new File( partFile.getPath() + PartialFile.EXT_LOCK );
            this.sleep = sleep;
            this.length = length;
            locked = new CountDownLatch( 1 );
            start();
            locked.await();
        }

        @Override
        public void run()
        {
            try
            {
                RandomAccessFile raf = new RandomAccessFile( lockFile, "rw" );
                try
                {
                    FileLock lock = raf.getChannel().lock( 0, 1, false );
                    locked.countDown();
                    FileOutputStream fos = new FileOutputStream( partFile );
                    try
                    {
                        for ( int i = 0, n = Math.abs( length ); i < n; i++ )
                        {
                            for ( long start = System.currentTimeMillis(); System.currentTimeMillis() - start < sleep; )
                            {
                                Thread.sleep( 10 );
                            }
                            fos.write( 65 );
                            fos.flush();
                            System.out.println( "  " + System.currentTimeMillis() + " Wrote byte " + ( i + 1 ) + "/"
                                + n );
                        }
                        if ( length >= 0 && !dstFile.setLastModified( System.currentTimeMillis() ) )
                        {
                            throw new IOException( "Could not update destination file" );
                        }
                    }
                    finally
                    {
                        fos.close();
                    }
                    lock.release();
                }
                finally
                {
                    raf.close();
                    lockFile.delete();
                }
            }
            catch ( Exception e )
            {
                error = e;
            }
        }

    }

    private static final boolean PROPER_LOCK_SUPPORT;

    static
    {
        String javaVersion = System.getProperty( "java.version" ).trim();
        boolean notJava5 = !javaVersion.startsWith( "1.5." );
        String osName = System.getProperty( "os.name" ).toLowerCase( Locale.ENGLISH );
        boolean windows = osName.contains( "windows" );
        PROPER_LOCK_SUPPORT = notJava5 || windows;
    }

    private StubRemoteAccessChecker remoteAccessChecker;

    private File dstFile;

    private File partFile;

    private File lockFile;

    private List<Closeable> closeables;

    private PartialFile newPartialFile( long resumeThreshold, int requestTimeout )
        throws Exception
    {
        PartialFile.Factory factory =
            new PartialFile.Factory( resumeThreshold >= 0, resumeThreshold, requestTimeout,
                                     new TestLoggerFactory().getLogger( "" ) );
        PartialFile partFile = factory.newInstance( dstFile, remoteAccessChecker );
        if ( partFile != null )
        {
            closeables.add( partFile );
        }
        return partFile;
    }

    @Before
    public void init()
        throws Exception
    {
        closeables = new ArrayList<Closeable>();
        remoteAccessChecker = new StubRemoteAccessChecker();
        dstFile = TestFileUtils.createTempFile( "Hello World!" );
        partFile = new File( dstFile.getPath() + PartialFile.EXT_PART );
        lockFile = new File( partFile.getPath() + PartialFile.EXT_LOCK );
    }

    @After
    public void exit()
    {
        for ( Closeable closeable : closeables )
        {
            try
            {
                closeable.close();
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testCloseNonResumableFile()
        throws Exception
    {
        PartialFile partialFile = newPartialFile( -1, 100 );
        assertNotNull( partialFile );
        assertNotNull( partialFile.getFile() );
        assertTrue( partialFile.getFile().getAbsolutePath(), partialFile.getFile().isFile() );
        partialFile.close();
        assertFalse( partialFile.getFile().getAbsolutePath(), partialFile.getFile().exists() );
    }

    @Test
    public void testCloseResumableFile()
        throws Exception
    {
        PartialFile partialFile = newPartialFile( 0, 100 );
        assertNotNull( partialFile );
        assertNotNull( partialFile.getFile() );
        assertTrue( partialFile.getFile().getAbsolutePath(), partialFile.getFile().isFile() );
        assertEquals( partFile, partialFile.getFile() );
        assertTrue( lockFile.getAbsolutePath(), lockFile.isFile() );
        partialFile.close();
        assertTrue( partialFile.getFile().getAbsolutePath(), partialFile.getFile().isFile() );
        assertFalse( lockFile.getAbsolutePath(), lockFile.exists() );
    }

    @Test
    public void testResumableFileCreationError()
        throws Exception
    {
        assertTrue( partFile.getAbsolutePath(), partFile.mkdirs() );
        PartialFile partialFile = newPartialFile( 0, 100 );
        assertNotNull( partialFile );
        assertFalse( partialFile.isResume() );
        assertFalse( lockFile.getAbsolutePath(), lockFile.exists() );
    }

    @Test
    public void testResumeThreshold()
        throws Exception
    {
        PartialFile partialFile = newPartialFile( 0, 100 );
        assertNotNull( partialFile );
        assertTrue( partialFile.isResume() );
        partialFile.close();
        partialFile = newPartialFile( 1, 100 );
        assertNotNull( partialFile );
        assertFalse( partialFile.isResume() );
        partialFile.close();
    }

    @Test( timeout = 10000 )
    public void testResumeConcurrently_RequestTimeout()
        throws Exception
    {
        assumeTrue( PROPER_LOCK_SUPPORT );
        ConcurrentWriter writer = new ConcurrentWriter( dstFile, 5 * 1000, 1 );
        try
        {
            newPartialFile( 0, 1000 );
            fail( "expected exception" );
        }
        catch ( Exception e )
        {
            assertTrue( e.getMessage().contains( "Timeout" ) );
        }
        writer.interrupt();
        writer.join();
    }

    @Test( timeout = 10000 )
    public void testResumeConcurrently_AwaitCompletion_ConcurrentWriterSucceeds()
        throws Exception
    {
        assumeTrue( PROPER_LOCK_SUPPORT );
        assertTrue( dstFile.setLastModified( System.currentTimeMillis() - 60 * 1000 ) );
        ConcurrentWriter writer = new ConcurrentWriter( dstFile, 100, 10 );
        assertNull( newPartialFile( 0, 500 ) );
        writer.join();
        assertNull( writer.error );
        assertEquals( 1, remoteAccessChecker.invocations );
    }

    @Test( timeout = 10000 )
    public void testResumeConcurrently_AwaitCompletion_ConcurrentWriterFails()
        throws Exception
    {
        assumeTrue( PROPER_LOCK_SUPPORT );
        assertTrue( dstFile.setLastModified( System.currentTimeMillis() - 60 * 1000 ) );
        ConcurrentWriter writer = new ConcurrentWriter( dstFile, 100, -10 );
        PartialFile partialFile = newPartialFile( 0, 500 );
        assertNotNull( partialFile );
        assertTrue( partialFile.isResume() );
        writer.join();
        assertNull( writer.error );
        assertEquals( 1, remoteAccessChecker.invocations );
    }

    @Test( timeout = 10000 )
    public void testResumeConcurrently_CheckRemoteAccess()
        throws Exception
    {
        assumeTrue( PROPER_LOCK_SUPPORT );
        remoteAccessChecker.exception = new IOException( "missing" );
        ConcurrentWriter writer = new ConcurrentWriter( dstFile, 1000, 1 );
        try
        {
            newPartialFile( 0, 1000 );
            fail( "expected exception" );
        }
        catch ( Exception e )
        {
            assertSame( remoteAccessChecker.exception, e );
        }
        writer.interrupt();
        writer.join();
    }

}
