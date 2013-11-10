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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.junit.Before;
import org.junit.Test;

public class ChecksumValidatorTest
{

    private static class StubChecksumPolicy
        implements ChecksumPolicy
    {

        boolean inspectAll;

        boolean tolerateFailure;

        private List<String> callbacks = new ArrayList<String>();

        private Object conclusion;

        public boolean onChecksumMatch( String algorithm, int kind )
        {
            callbacks.add( String.format( "match(%s, %04x)", algorithm, kind ) );
            if ( inspectAll )
            {
                if ( conclusion == null )
                {
                    conclusion = true;
                }
                return false;
            }
            return true;
        }

        public void onChecksumMismatch( String algorithm, int kind, ChecksumFailureException exception )
            throws ChecksumFailureException
        {
            callbacks.add( String.format( "mismatch(%s, %04x)", algorithm, kind ) );
            if ( inspectAll )
            {
                conclusion = exception;
                return;
            }
            throw exception;
        }

        public void onChecksumError( String algorithm, int kind, ChecksumFailureException exception )
            throws ChecksumFailureException
        {
            callbacks.add( String.format( "error(%s, %04x, %s)", algorithm, kind, exception.getCause().getMessage() ) );
        }

        public void onNoMoreChecksums()
            throws ChecksumFailureException
        {
            callbacks.add( String.format( "noMore()" ) );
            if ( conclusion instanceof ChecksumFailureException )
            {
                throw (ChecksumFailureException) conclusion;
            }
            else if ( !Boolean.TRUE.equals( conclusion ) )
            {
                throw new ChecksumFailureException( "no checksums" );
            }
        }

        public void onTransferRetry()
        {
            callbacks.add( String.format( "retry()" ) );
        }

        public boolean onTransferChecksumFailure( ChecksumFailureException exception )
        {
            callbacks.add( String.format( "fail(%s)", exception.getMessage() ) );
            return tolerateFailure;
        }

        void assertCallbacks( String... callbacks )
        {
            assertEquals( Arrays.asList( callbacks ), this.callbacks );
        }

    }

    private static class StubChecksumFetcher
        implements ChecksumValidator.ChecksumFetcher
    {

        Map<URI, Object> checksums = new HashMap<URI, Object>();

        List<File> checksumFiles = new ArrayList<File>();

        private List<URI> fetchedFiles = new ArrayList<URI>();

        public boolean fetchChecksum( URI remote, File local )
            throws Exception
        {
            fetchedFiles.add( remote );
            Object checksum = checksums.get( remote );
            if ( checksum == null )
            {
                return false;
            }
            if ( checksum instanceof Exception )
            {
                throw (Exception) checksum;
            }
            TestFileUtils.writeString( local, checksum.toString() );
            checksumFiles.add( local );
            return true;
        }

        void mock( String algo, Object value )
        {
            checksums.put( toUri( algo ), value );
        }

        void assertFetchedFiles( String... algos )
        {
            List<URI> expected = new ArrayList<URI>();
            for ( String algo : algos )
            {
                expected.add( toUri( algo ) );
            }
            assertEquals( expected, fetchedFiles );
        }

        private static URI toUri( String algo )
        {
            return newChecksum( algo ).getLocation();
        }

    }

    private static final String SHA1 = "SHA-1";

    private static final String MD5 = "MD5";

    private StubChecksumPolicy policy;

    private StubChecksumFetcher fetcher;

    private File dataFile;

    private static RepositoryLayout.Checksum newChecksum( String algo )
    {
        return RepositoryLayout.Checksum.forLocation( URI.create( "file" ), algo );
    }

    private List<RepositoryLayout.Checksum> newChecksums( String... algos )
    {
        List<RepositoryLayout.Checksum> checksums = new ArrayList<RepositoryLayout.Checksum>();
        for ( String algo : algos )
        {
            checksums.add( newChecksum( algo ) );
        }
        return checksums;
    }

    private ChecksumValidator newValidator( String... algos )
    {
        return new ChecksumValidator( new TestLoggerFactory().getLogger( "" ), dataFile, new TestFileProcessor(),
                                      fetcher, policy, newChecksums( algos ) );
    }

    private Map<String, ?> checksums( String... algoDigestPairs )
    {
        Map<String, Object> checksums = new LinkedHashMap<String, Object>();
        for ( int i = 0; i < algoDigestPairs.length; i += 2 )
        {
            String algo = algoDigestPairs[i];
            String digest = algoDigestPairs[i + 1];
            if ( digest == null )
            {
                checksums.put( algo, new IOException( "error" ) );
            }
            else
            {
                checksums.put( algo, digest );
            }
        }
        return checksums;
    }

    @Before
    public void init()
        throws Exception
    {
        dataFile = TestFileUtils.createTempFile( "" );
        dataFile.delete();
        policy = new StubChecksumPolicy();
        fetcher = new StubChecksumFetcher();
    }

    @Test
    public void testValidate_NullPolicy()
        throws Exception
    {
        policy = null;
        ChecksumValidator validator = newValidator( SHA1 );
        validator.validate( checksums( SHA1, "ignored" ), null );
        fetcher.assertFetchedFiles();
    }

    @Test
    public void testValidate_AcceptOnFirstMatch()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1 );
        fetcher.mock( SHA1, "foo" );
        validator.validate( checksums( SHA1, "foo" ), null );
        fetcher.assertFetchedFiles( SHA1 );
        policy.assertCallbacks( "match(SHA-1, 0000)" );
    }

    @Test
    public void testValidate_FailOnFirstMismatch()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1 );
        fetcher.mock( SHA1, "foo" );
        try
        {
            validator.validate( checksums( SHA1, "not-foo" ), null );
            fail( "expected exception" );
        }
        catch ( ChecksumFailureException e )
        {
            assertEquals( "foo", e.getExpected() );
            assertEquals( "not-foo", e.getActual() );
            assertTrue( e.isRetryWorthy() );
        }
        fetcher.assertFetchedFiles( SHA1 );
        policy.assertCallbacks( "mismatch(SHA-1, 0000)" );
    }

    @Test
    public void testValidate_AcceptOnEnd()
        throws Exception
    {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( SHA1, "foo" );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( SHA1, "foo", MD5, "bar" ), null );
        fetcher.assertFetchedFiles( SHA1, MD5 );
        policy.assertCallbacks( "match(SHA-1, 0000)", "match(MD5, 0000)", "noMore()" );
    }

    @Test
    public void testValidate_FailOnEnd()
        throws Exception
    {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( SHA1, "foo" );
        fetcher.mock( MD5, "bar" );
        try
        {
            validator.validate( checksums( SHA1, "not-foo", MD5, "bar" ), null );
            fail( "expected exception" );
        }
        catch ( ChecksumFailureException e )
        {
            assertEquals( "foo", e.getExpected() );
            assertEquals( "not-foo", e.getActual() );
            assertTrue( e.isRetryWorthy() );
        }
        fetcher.assertFetchedFiles( SHA1, MD5 );
        policy.assertCallbacks( "mismatch(SHA-1, 0000)", "match(MD5, 0000)", "noMore()" );
    }

    @Test
    public void testValidate_InlinedBeforeExternal()
        throws Exception
    {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( SHA1, "foo" );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( SHA1, "foo", MD5, "bar" ), checksums( SHA1, "foo", MD5, "bar" ) );
        fetcher.assertFetchedFiles( SHA1, MD5 );
        policy.assertCallbacks( "match(SHA-1, 0001)", "match(MD5, 0001)", "match(SHA-1, 0000)", "match(MD5, 0000)",
                                "noMore()" );
    }

    @Test
    public void testValidate_CaseInsensitive()
        throws Exception
    {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator( SHA1 );
        fetcher.mock( SHA1, "FOO" );
        validator.validate( checksums( SHA1, "foo" ), checksums( SHA1, "foo" ) );
        policy.assertCallbacks( "match(SHA-1, 0001)", "match(SHA-1, 0000)", "noMore()" );
    }

    @Test
    public void testValidate_MissingRemoteChecksum()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( MD5, "bar" ), null );
        fetcher.assertFetchedFiles( SHA1, MD5 );
        policy.assertCallbacks( "match(MD5, 0000)" );
    }

    @Test
    public void testValidate_InaccessibleRemoteChecksum()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( SHA1, new IOException( "inaccessible" ) );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( MD5, "bar" ), null );
        fetcher.assertFetchedFiles( SHA1, MD5 );
        policy.assertCallbacks( "error(SHA-1, 0000, inaccessible)", "match(MD5, 0000)" );
    }

    @Test
    public void testValidate_InaccessibleLocalChecksum()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( SHA1, "foo" );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( SHA1, null, MD5, "bar" ), null );
        fetcher.assertFetchedFiles( MD5 );
        policy.assertCallbacks( "error(SHA-1, 0000, error)", "match(MD5, 0000)" );
    }

    @Test
    public void testHandle_Accept()
        throws Exception
    {
        policy.tolerateFailure = true;
        ChecksumValidator validator = newValidator( SHA1 );
        assertEquals( true, validator.handle( new ChecksumFailureException( "accept" ) ) );
        policy.assertCallbacks( "fail(accept)" );
    }

    @Test
    public void testHandle_Reject()
        throws Exception
    {
        policy.tolerateFailure = false;
        ChecksumValidator validator = newValidator( SHA1 );
        assertEquals( false, validator.handle( new ChecksumFailureException( "reject" ) ) );
        policy.assertCallbacks( "fail(reject)" );
    }

    @Test
    public void testRetry_ResetPolicy()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1 );
        validator.retry();
        policy.assertCallbacks( "retry()" );
    }

    @Test
    public void testRetry_RemoveTempFiles()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1 );
        fetcher.mock( SHA1, "foo" );
        validator.validate( checksums( SHA1, "foo" ), null );
        fetcher.assertFetchedFiles( SHA1 );
        assertEquals( 1, fetcher.checksumFiles.size() );
        for ( File file : fetcher.checksumFiles )
        {
            assertTrue( file.getAbsolutePath(), file.isFile() );
        }
        validator.retry();
        for ( File file : fetcher.checksumFiles )
        {
            assertFalse( file.getAbsolutePath(), file.exists() );
        }
    }

    @Test
    public void testCommit_SaveChecksumFiles()
        throws Exception
    {
        policy.inspectAll = true;
        ChecksumValidator validator = newValidator( SHA1, MD5 );
        fetcher.mock( MD5, "bar" );
        validator.validate( checksums( SHA1, "foo", MD5, "bar" ), checksums( SHA1, "foo" ) );
        assertEquals( 1, fetcher.checksumFiles.size() );
        for ( File file : fetcher.checksumFiles )
        {
            assertTrue( file.getAbsolutePath(), file.isFile() );
        }
        validator.commit();
        File checksumFile = new File( dataFile.getPath() + ".sha1" );
        assertTrue( checksumFile.getAbsolutePath(), checksumFile.isFile() );
        assertEquals( "foo", TestFileUtils.readString( checksumFile ) );
        checksumFile = new File( dataFile.getPath() + ".md5" );
        assertTrue( checksumFile.getAbsolutePath(), checksumFile.isFile() );
        assertEquals( "bar", TestFileUtils.readString( checksumFile ) );
        for ( File file : fetcher.checksumFiles )
        {
            assertFalse( file.getAbsolutePath(), file.exists() );
        }
    }

    @Test
    public void testClose_RemoveTempFiles()
        throws Exception
    {
        ChecksumValidator validator = newValidator( SHA1 );
        fetcher.mock( SHA1, "foo" );
        validator.validate( checksums( SHA1, "foo" ), null );
        fetcher.assertFetchedFiles( SHA1 );
        assertEquals( 1, fetcher.checksumFiles.size() );
        for ( File file : fetcher.checksumFiles )
        {
            assertTrue( file.getAbsolutePath(), file.isFile() );
        }
        validator.close();
        for ( File file : fetcher.checksumFiles )
        {
            assertFalse( file.getAbsolutePath(), file.exists() );
        }
    }

}
