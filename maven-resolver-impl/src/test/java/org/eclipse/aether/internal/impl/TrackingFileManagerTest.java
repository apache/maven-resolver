package org.eclipse.aether.internal.impl;

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

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.junit.Test;

/**
 */
public class TrackingFileManagerTest
{

    @Test
    public void testRead()
        throws Exception
    {
        TrackingFileManager tfm = new TrackingFileManager();

        File propFile = TestFileUtils.createTempFile( "#COMMENT\nkey1=value1\nkey2 : value2" );
        Properties props = tfm.read( propFile );

        assertNotNull( props );
        assertEquals( String.valueOf( props ), 2, props.size() );
        assertEquals( "value1", props.get( "key1" ) );
        assertEquals( "value2", props.get( "key2" ) );

        assertTrue( "Leaked file: " + propFile, propFile.delete() );

        props = tfm.read( propFile );
        assertNull( String.valueOf( props ), props );
        assertEquals( 1, tfm.getFileLockCache().asMap().size() );
    }

    @Test
    public void testReadNoFileLeak()
        throws Exception
    {
        TrackingFileManager tfm = new TrackingFileManager();

        for ( int i = 0; i < 1000; i++ )
        {
            File propFile = TestFileUtils.createTempFile( "#COMMENT\nkey1=value1\nkey2 : value2" );
            assertNotNull( tfm.read( propFile ) );
            assertTrue( "Leaked file: " + propFile, propFile.delete() );
        }

        int size = tfm.getFileLockCache().asMap().size();
        assertTrue(size > 900);
        assertTrue(size <= 1000);
    }

    @Test
    public void testUpdate()
        throws Exception
    {
        TrackingFileManager tfm = new TrackingFileManager();

        // NOTE: The excessive repetitions are to check the update properly truncates the file
        File propFile = TestFileUtils.createTempFile( "key1=value1\nkey2 : value2\n".getBytes( StandardCharsets.UTF_8 ), 1000 );

        Map<String, String> updates = new HashMap<String, String>();
        updates.put( "key1", "v" );
        updates.put( "key2", null );

        tfm.update( propFile, updates );
        assertEquals( 1, tfm.getFileLockCache().asMap().size() );

        Properties props = tfm.read( propFile );

        assertNotNull( props );
        assertEquals( String.valueOf( props ), 1, props.size() );
        assertEquals( "v", props.get( "key1" ) );
        assertNull( String.valueOf( props.get( "key2" ) ), props.get( "key2" ) );
        assertEquals( 1, tfm.getFileLockCache().asMap().size() );
    }

    @Test
    public void testUpdateNoFileLeak()
        throws Exception
    {
        TrackingFileManager tfm = new TrackingFileManager();

        Map<String, String> updates = new HashMap<String, String>();
        updates.put( "k", "v" );

        for ( int i = 0; i < 1000; i++ )
        {
            File propFile = TestFileUtils.createTempFile( "#COMMENT\nkey1=value1\nkey2 : value2" );
            assertNotNull( tfm.update( propFile, updates ) );
            assertTrue( "Leaked file: " + propFile, propFile.delete() );
        }
        int size = tfm.getFileLockCache().asMap().size();
        assertTrue(size > 900);
        assertTrue(size <= 1000);
    }

    @Test
    public void testLockingOnCanonicalPath()
        throws Exception
    {
        final TrackingFileManager tfm = new TrackingFileManager();

        final File propFile = TestFileUtils.createTempFile( "#COMMENT\nkey1=value1\nkey2 : value2" );

        final List<Throwable> errors = Collections.synchronizedList( new ArrayList<Throwable>() );

        Thread[] threads = new Thread[4];
        for ( int i = 0; i < threads.length; i++ )
        {
            String path = propFile.getParent();
            for ( int j = 0; j < i; j++ )
            {
                path += "/.";
            }
            path += "/" + propFile.getName();
            final File file = new File( path );

            threads[i] = new Thread()
            {
                public void run()
                {
                    try
                    {
                        for ( int i = 0; i < 1000; i++ )
                        {
                            assertNotNull( tfm.read( file ) );
                        }
                    }
                    catch ( Throwable e )
                    {
                        errors.add( e );
                    }
                }
            };
        }

        for ( Thread thread1 : threads )
        {
            thread1.start();
        }

        for ( Thread thread : threads )
        {
            thread.join();
        }

        assertEquals( Collections.emptyList(), errors );
        assertEquals( 4, tfm.getFileLockCache().asMap().size() );
    }

    @Test
    public void testCacheSizeConfig()
            throws Exception
    {
        TrackingFileManager tfm = new TrackingFileManager(
                Collections.singletonMap( ConfigurationProperties.TRACKING_FILE_MANAGER_FILE_LOCK_CACHE_SIZE, 5L )
        );

        for ( int i = 0; i < 1000; i++ )
        {
            File propFile = TestFileUtils.createTempFile( "#COMMENT\nkey1=value1\nkey2 : value2" );
            assertNotNull( tfm.read( propFile ) );
        }

        assertEquals( 5, tfm.getFileLockCache().asMap().size() );
    }
}
