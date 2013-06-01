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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.spi.io.FileProcessor.ProgressListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultFileProcessorTest
{

    private File targetDir;

    private DefaultFileProcessor fileProcessor;

    @Before
    public void setup()
        throws IOException
    {
        targetDir = TestFileUtils.createTempDir( getClass().getSimpleName() );
        fileProcessor = new DefaultFileProcessor();
    }

    @After
    public void teardown()
        throws Exception
    {
        TestFileUtils.delete( targetDir );
        fileProcessor = null;
    }

    @Test
    public void testCopy()
        throws IOException
    {
        String data = "testCopy\nasdf";
        File file = TestFileUtils.createTempFile( data );
        File target = new File( targetDir, "testCopy.txt" );

        fileProcessor.copy( file, target );

        assertEquals( data, TestFileUtils.getString( file ) );

        file.delete();
    }

    @Test
    public void testOverwrite()
        throws IOException
    {
        String data = "testCopy\nasdf";
        File file = TestFileUtils.createTempFile( data );

        for ( int i = 0; i < 5; i++ )
        {
            File target = new File( targetDir, "testCopy.txt" );
            fileProcessor.copy( file, target );
            assertEquals( data, TestFileUtils.getString( file ) );
        }

        file.delete();
    }

    @Test
    public void testCopyEmptyFile()
        throws IOException
    {
        File file = TestFileUtils.createTempFile( "" );
        File target = new File( targetDir, "testCopyEmptyFile" );
        target.delete();
        fileProcessor.copy( file, target );
        assertTrue( "empty file was not copied", target.exists() && target.length() == 0 );
        target.delete();
    }

    @Test
    public void testProgressingChannel()
        throws IOException
    {
        File file = TestFileUtils.createTempFile( "test" );
        File target = new File( targetDir, "testProgressingChannel" );
        target.delete();
        final AtomicInteger progressed = new AtomicInteger();
        ProgressListener listener = new ProgressListener()
        {
            public void progressed( ByteBuffer buffer )
                throws IOException
            {
                progressed.addAndGet( buffer.remaining() );
            }
        };
        fileProcessor.copy( file, target, listener );
        assertTrue( "file was not created", target.isFile() );
        assertEquals( "file was not fully copied", 4, target.length() );
        assertEquals( "listener not called", 4, progressed.intValue() );
        target.delete();
    }

}
