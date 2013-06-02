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
package org.eclipse.aether.spi.connector.layout;

import static org.junit.Assert.*;

import java.net.URI;

import org.junit.Test;

import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;

public class ChecksumTest
{

    @Test
    public void testForLocation()
    {
        Checksum cs = Checksum.forLocation( URI.create( "dir/sub%20dir/file.txt" ), "SHA-1" );
        assertEquals( "SHA-1", cs.getAlgorithm() );
        assertEquals( "dir/sub%20dir/file.txt.sha1", cs.getLocation().toString() );

        cs = Checksum.forLocation( URI.create( "dir/sub%20dir/file.txt" ), "MD5" );
        assertEquals( "MD5", cs.getAlgorithm() );
        assertEquals( "dir/sub%20dir/file.txt.md5", cs.getLocation().toString() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testForLocation_WithQueryParams()
    {
        Checksum.forLocation( URI.create( "file.php?param=1" ), "SHA-1" );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testForLocation_WithFragment()
    {
        Checksum.forLocation( URI.create( "file.html#fragment" ), "SHA-1" );
    }

}
