package org.eclipse.aether.connector.basic;

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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithm;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySupport;
import org.eclipse.aether.util.ChecksumUtils;

/**
 * Test implementation of {@link ChecksumAlgorithmFactorySelector}.
 */
public class TestChecksumAlgorithmSelector
        implements ChecksumAlgorithmFactorySelector
{
    public static final String SHA512 = "SHA-512";

    public static final String SHA256 = "SHA-256";

    public static final String SHA1 = "SHA-1";

    public static final String MD5 = "MD5";

    public static final String TEST_CHECKSUM = "test";

    public static final String TEST_CHECKSUM_VALUE = "01020304";

    @Override
    public Set<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories()
    {
        return Collections.emptySet(); // irrelevant
    }

    @Override
    public ChecksumAlgorithmFactory select( final String algorithm )
    {
        if ( TEST_CHECKSUM.equals( algorithm ) )
        {
            return new ChecksumAlgorithmFactorySupport( TEST_CHECKSUM, "test" )
            {
                @Override
                public ChecksumAlgorithm getAlgorithm()
                {
                    return new ChecksumAlgorithm()
                    {
                        @Override
                        public void update( final ByteBuffer input )
                        {

                        }

                        @Override
                        public String checksum()
                        {
                            return TEST_CHECKSUM_VALUE;
                        }
                    };
                }
            };
        }
        return new MessageDigestChecksumAlgorithmFactory( algorithm );
    }

    private static class MessageDigestChecksumAlgorithmFactory
            extends ChecksumAlgorithmFactorySupport
    {
        public MessageDigestChecksumAlgorithmFactory( String name )
        {
            super( name, name.replace( "-", "" ).toLowerCase( Locale.ENGLISH ) );
        }

        @Override
        public ChecksumAlgorithm getAlgorithm()
        {
            try
            {
                MessageDigest messageDigest = MessageDigest.getInstance( getName() );
                return new ChecksumAlgorithm()
                {
                    @Override
                    public void update( final ByteBuffer input )
                    {
                        messageDigest.update( input );
                    }

                    @Override
                    public String checksum()
                    {
                        return ChecksumUtils.toHexString( messageDigest.digest() );
                    }
                };
            }
            catch ( NoSuchAlgorithmException e )
            {
                throw new IllegalArgumentException( "Algorithm '" + getName() + "' not supported." );
            }
        }
    }
}
