package org.eclipse.aether.spi.connector.layout;

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

import java.net.URI;

import org.junit.Test;

import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;

public class ChecksumTest
{

    @Test
    public void testForLocation()
    {
        Checksum cs = Checksum.forLocation( URI.create( "dir/sub%20dir/file.txt" ), "SHA-512" );
        assertEquals( "SHA-512", cs.getAlgorithm() );
        assertEquals( "dir/sub%20dir/file.txt.sha512", cs.getLocation().toString() );

        cs = Checksum.forLocation( URI.create( "dir/sub%20dir/file.txt" ), "SHA-256" );
        assertEquals( "SHA-256", cs.getAlgorithm() );
        assertEquals( "dir/sub%20dir/file.txt.sha256", cs.getLocation().toString() );

        cs = Checksum.forLocation( URI.create( "dir/sub%20dir/file.txt" ), "SHA-1" );
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
