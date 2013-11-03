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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.Before;
import org.junit.Test;

public class FailChecksumPolicyTest
{

    private FailChecksumPolicy policy;

    private ChecksumFailureException exception;

    @Before
    public void setup()
    {
        policy = new FailChecksumPolicy( null, new TransferResource( "file:/dev/null", "file.txt", null, null ) );
        exception = new ChecksumFailureException( "test" );
    }

    @Test
    public void testOnTransferChecksumFailure()
    {
        assertFalse( policy.onTransferChecksumFailure( exception ) );
    }

    @Test
    public void testOnChecksumMatch()
    {
        assertTrue( policy.onChecksumMatch( "SHA-1", 0 ) );
        assertTrue( policy.onChecksumMatch( "SHA-1", ChecksumPolicy.KIND_UNOFFICIAL ) );
    }

    @Test
    public void testOnChecksumMismatch()
        throws Exception
    {
        try
        {
            policy.onChecksumMismatch( "SHA-1", 0, exception );
            fail( "No exception" );
        }
        catch ( ChecksumFailureException e )
        {
            assertSame( exception, e );
        }
        policy.onChecksumMismatch( "SHA-1", ChecksumPolicy.KIND_UNOFFICIAL, exception );
    }

    @Test
    public void testOnChecksumError()
        throws Exception
    {
        policy.onChecksumError( "SHA-1", 0, exception );
    }

    @Test
    public void testOnNoMoreChecksums()
    {
        try
        {
            policy.onNoMoreChecksums();
            fail( "No exception" );
        }
        catch ( ChecksumFailureException e )
        {
            assertTrue( e.getMessage().contains( "no checksums available" ) );
        }
    }

}
