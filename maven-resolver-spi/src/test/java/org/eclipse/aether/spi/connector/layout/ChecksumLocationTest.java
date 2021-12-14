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

import java.net.URI;

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithm;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySupport;
import org.junit.Test;

import org.eclipse.aether.spi.connector.layout.RepositoryLayout.ChecksumLocation;

import static org.junit.Assert.assertEquals;

public class ChecksumLocationTest
{
    private ChecksumAlgorithmFactory SHA512 = new ChecksumAlgorithmFactorySupport("SHA-512", "sha512") {
        @Override
        public ChecksumAlgorithm getAlgorithm() {
            throw new RuntimeException("this should not happen");
        }
    };

    private ChecksumAlgorithmFactory SHA256 = new ChecksumAlgorithmFactorySupport("SHA-256", "sha256") {
        @Override
        public ChecksumAlgorithm getAlgorithm() {
            throw new RuntimeException("this should not happen");
        }
    };

    private ChecksumAlgorithmFactory SHA1 = new ChecksumAlgorithmFactorySupport("SHA-1", "sha1") {
        @Override
        public ChecksumAlgorithm getAlgorithm() {
            throw new RuntimeException("this should not happen");
        }
    };

    private ChecksumAlgorithmFactory MD5 = new ChecksumAlgorithmFactorySupport("MD5", "md5") {
        @Override
        public ChecksumAlgorithm getAlgorithm() {
            throw new RuntimeException("this should not happen");
        }
    };

    @Test
    public void testForLocation()
    {
        ChecksumLocation cs = ChecksumLocation.forLocation( URI.create( "dir/sub%20dir/file.txt" ), SHA512 );
        assertEquals( SHA512, cs.getChecksumAlgorithmFactory() );
        assertEquals( "dir/sub%20dir/file.txt.sha512", cs.getLocation().toString() );

        cs = ChecksumLocation.forLocation( URI.create( "dir/sub%20dir/file.txt" ), SHA256 );
        assertEquals( SHA256, cs.getChecksumAlgorithmFactory() );
        assertEquals( "dir/sub%20dir/file.txt.sha256", cs.getLocation().toString() );

        cs = ChecksumLocation.forLocation( URI.create( "dir/sub%20dir/file.txt" ), SHA1 );
        assertEquals( SHA1, cs.getChecksumAlgorithmFactory() );
        assertEquals( "dir/sub%20dir/file.txt.sha1", cs.getLocation().toString() );

        cs = ChecksumLocation.forLocation( URI.create( "dir/sub%20dir/file.txt" ), MD5 );
        assertEquals( MD5, cs.getChecksumAlgorithmFactory() );
        assertEquals( "dir/sub%20dir/file.txt.md5", cs.getLocation().toString() );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testForLocation_WithQueryParams()
    {
        ChecksumLocation.forLocation( URI.create( "file.php?param=1" ), SHA1 );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testForLocation_WithFragment()
    {
        ChecksumLocation.forLocation( URI.create( "file.html#fragment" ), SHA1 );
    }

}
