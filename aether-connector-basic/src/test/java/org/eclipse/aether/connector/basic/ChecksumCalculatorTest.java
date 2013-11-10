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
package org.eclipse.aether.connector.basic;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.junit.Before;
import org.junit.Test;

public class ChecksumCalculatorTest
{

    private static final String SHA1 = "SHA-1";

    private static final String MD5 = "MD5";

    private File file;

    private ChecksumCalculator newCalculator( String... algos )
    {
        List<RepositoryLayout.Checksum> checksums = new ArrayList<RepositoryLayout.Checksum>();
        for ( String algo : algos )
        {
            checksums.add( new RepositoryLayout.Checksum( algo, URI.create( "irrelevant" ) ) );
        }
        return ChecksumCalculator.newInstance( file, checksums );
    }

    private ByteBuffer toBuffer( String data )
    {
        try
        {
            return ByteBuffer.wrap( data.getBytes( "UTF-8" ) );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Before
    public void init()
        throws Exception
    {
        file = TestFileUtils.createTempFile( "Hello World!" );
    }

    @Test
    public void testNoOffset()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.init( 0 );
        calculator.update( toBuffer( "Hello World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertEquals( "ed076287532e86365e841e92bfc50d8c", digests.get( MD5 ) );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testWithOffset()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.init( 6 );
        calculator.update( toBuffer( "World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertEquals( "ed076287532e86365e841e92bfc50d8c", digests.get( MD5 ) );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testWithExcessiveOffset()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.init( 100 );
        calculator.update( toBuffer( "World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertTrue( digests.get( SHA1 ) instanceof IOException );
        assertTrue( digests.get( MD5 ) instanceof IOException );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testUnknownAlgorithm()
    {
        ChecksumCalculator calculator = newCalculator( "unknown", SHA1 );
        calculator.init( 0 );
        calculator.update( toBuffer( "Hello World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertTrue( digests.get( "unknown" ) instanceof NoSuchAlgorithmException );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testNoInitCall()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.update( toBuffer( "Hello World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertEquals( "ed076287532e86365e841e92bfc50d8c", digests.get( MD5 ) );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testRestart()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.init( 0 );
        calculator.update( toBuffer( "Ignored" ) );
        calculator.init( 0 );
        calculator.update( toBuffer( "Hello World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertEquals( "ed076287532e86365e841e92bfc50d8c", digests.get( MD5 ) );
        assertEquals( 2, digests.size() );
    }

    @Test
    public void testRestartAfterError()
    {
        ChecksumCalculator calculator = newCalculator( SHA1, MD5 );
        calculator.init( 100 );
        calculator.init( 0 );
        calculator.update( toBuffer( "Hello World!" ) );
        Map<String, Object> digests = calculator.get();
        assertNotNull( digests );
        assertEquals( "2ef7bde608ce5404e97d5f042f95f89f1c232871", digests.get( SHA1 ) );
        assertEquals( "ed076287532e86365e841e92bfc50d8c", digests.get( MD5 ) );
        assertEquals( 2, digests.size() );
    }

}
