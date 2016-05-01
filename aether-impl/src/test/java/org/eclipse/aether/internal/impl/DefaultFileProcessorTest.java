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
        TestFileUtils.deleteFile( targetDir );
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

        assertEquals( data, TestFileUtils.readString( file ) );

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
            assertEquals( data, TestFileUtils.readString( file ) );
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
