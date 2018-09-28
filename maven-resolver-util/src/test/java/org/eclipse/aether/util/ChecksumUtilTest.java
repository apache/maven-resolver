package org.eclipse.aether.util;

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

import static org.eclipse.aether.internal.test.util.TestFileUtils.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.aether.util.ChecksumUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ChecksumUtilTest
{
    private static final String EMPTY = "EMPTY";
    private static final String PATTERN = "PATTERN";
    private static final String TEXT = "TEXT";
    
    private Map<String, File> files = new HashMap<>(3);
    
    private Map<String, byte[]> bytes = new HashMap<>(3);
    
    private static Map<String, String> emptyChecksums = new HashMap<>();

    private static Map<String, String> patternChecksums = new HashMap<>();

    private static Map<String, String> textChecksums = new HashMap<>();

    private Map<String, Map<String, String>> sums = new HashMap<>();

    @BeforeClass
    public static void beforeClass()
        throws IOException
    {
        emptyChecksums.put( "MD5", "d41d8cd98f00b204e9800998ecf8427e" );
        emptyChecksums.put( "SHA-1", "da39a3ee5e6b4b0d3255bfef95601890afd80709" );
        patternChecksums.put( "MD5", "14f01d6c7de7d4cf0a4887baa3528b5a" );
        patternChecksums.put( "SHA-1", "feeeda19f626f9b0ef6cbf5948c1ec9531694295" );
        textChecksums.put( "MD5", "12582d1a662cefe3385f2113998e43ed" );
        textChecksums.put( "SHA-1", "a8ae272db549850eef2ff54376f8cac2770745ee" );
    }

    @Before
    public void before()
        throws IOException
    {
        sums.clear();

        byte[] emptyBytes = new byte[0];
        bytes.put( EMPTY, emptyBytes );
        files.put( EMPTY, createTempFile( emptyBytes, 0 ) );
        sums.put( EMPTY, emptyChecksums );

        byte[] patternBytes = writeBytes( new byte[] { 0, 1, 2, 4, 8, 16, 32, 64, 127, -1, -2, -4, -8, -16, -32, -64, -127 }, 1000 );
        bytes.put( PATTERN, patternBytes );
        files.put( PATTERN, createTempFile( patternBytes, 1 ) );
        sums.put( PATTERN, patternChecksums );

        byte[] textBytes = writeBytes( "the quick brown fox jumps over the lazy dog\n".getBytes( StandardCharsets.UTF_8 ), 500 );
        bytes.put( TEXT, textBytes );
        files.put( TEXT, createTempFile( textBytes, 1 ) );
        sums.put( TEXT, textChecksums );

    }

    @Test
    public void testEquality()
        throws Throwable
    {
        Map<String, Object> checksums = null;

        for ( Map.Entry<String,File> fileEntry : files.entrySet() )
        {

            checksums = ChecksumUtils.calc( fileEntry.getValue(), Arrays.asList( "SHA-1", "MD5" ) );

            for ( Entry<String, Object> entry : checksums.entrySet() )
            {
                if ( entry.getValue() instanceof Throwable )
                {
                    throw (Throwable) entry.getValue();
                }
                String actual = entry.getValue().toString();
                String expected = sums.get( fileEntry.getKey() ).get( entry.getKey() );
                assertEquals( String.format( "checksums do not match for '%s', algorithm '%s'", fileEntry.getValue().getName(),
                                             entry.getKey() ), expected, actual );
            }
            assertTrue( "Could not delete file", fileEntry.getValue().delete() );
        }
    }

    @Test
    public void testFileHandleLeakage()
        throws IOException
    {
        for ( File file : files.values() )
        {
            for ( int i = 0; i < 150; i++ )
            {
                ChecksumUtils.calc( file, Arrays.asList( "SHA-1", "MD5" ) );
            }
            assertTrue( "Could not delete file", file.delete() );
        }

    }

    @Test
    public void testRead()
        throws IOException
    {
        for ( Map<String, String> checksums : sums.values() )
        {
            String sha1 = checksums.get( "SHA-1" );
            String md5 = checksums.get( "MD5" );

            File sha1File = createTempFile( sha1 );
            File md5File = createTempFile( md5 );

            assertEquals( sha1, ChecksumUtils.read( sha1File ) );
            assertEquals( md5, ChecksumUtils.read( md5File ) );

            assertTrue( "ChecksumUtils leaks file handles (cannot delete checksums.sha1)", sha1File.delete() );
            assertTrue( "ChecksumUtils leaks file handles (cannot delete checksums.md5)", md5File.delete() );
        }
    }

    @Test
    public void testReadSpaces()
        throws IOException
    {
        for ( Map<String, String> checksums : sums.values() )
        {
            String sha1 = checksums.get( "SHA-1" );
            String md5 = checksums.get( "MD5" );

            File sha1File = createTempFile( "sha1-checksum = " + sha1 );
            File md5File = createTempFile( md5 + " test" );

            assertEquals( sha1, ChecksumUtils.read( sha1File ) );
            assertEquals( md5, ChecksumUtils.read( md5File ) );

            assertTrue( "ChecksumUtils leaks file handles (cannot delete checksums.sha1)", sha1File.delete() );
            assertTrue( "ChecksumUtils leaks file handles (cannot delete checksums.md5)", md5File.delete() );
        }
    }

    @Test
    public void testReadEmptyFile()
        throws IOException
    {
        File file = createTempFile( "" );

        assertEquals( "", ChecksumUtils.read( file ) );

        assertTrue( "ChecksumUtils leaks file handles (cannot delete checksum.empty)", file.delete() );
    }

    @Test
    public void testToHexString()
    {
        assertEquals( null, ChecksumUtils.toHexString( null ) );
        assertEquals( "", ChecksumUtils.toHexString( new byte[] {} ) );
        assertEquals( "00", ChecksumUtils.toHexString( new byte[] { 0 } ) );
        assertEquals( "ff", ChecksumUtils.toHexString( new byte[] { -1 } ) );
        assertEquals( "00017f", ChecksumUtils.toHexString( new byte[] { 0, 1, 127 } ) );
    }
    
    @Test
    public void testCalcWithByteArray() throws Throwable
    {
        Map<String, Object> checksums = null;

        for ( Map.Entry<String, byte[]> bytesEntry : bytes.entrySet() )
        {
            checksums = ChecksumUtils.calc( bytesEntry.getValue(), Arrays.asList( "SHA-1", "MD5" ) );

            for ( Entry<String, Object> entry : checksums.entrySet() )
            {
                if ( entry.getValue() instanceof Throwable )
                {
                    throw (Throwable) entry.getValue();
                }
                String actual = entry.getValue().toString();
                String expected = sums.get( bytesEntry.getKey() ).get( entry.getKey() );
                assertEquals( String.format( "checksums do not match for '%s', algorithm '%s'", bytesEntry.getKey(),
                                             entry.getKey() ), expected, actual );
            }
        }
    }

    private byte[] writeBytes( byte[] pattern, int repeat )
    {
        byte[] result = new byte[pattern.length * repeat];
        for ( int i = 0; i < repeat; i++ )
        {
            System.arraycopy( pattern, 0, result, i * pattern.length, pattern.length );
        }
        return result;
    }
}

