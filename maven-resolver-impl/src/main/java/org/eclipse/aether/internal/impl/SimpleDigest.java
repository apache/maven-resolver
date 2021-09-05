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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A simple digester for strings. It will traverse through a list of digest algorithms and pick the
 * strongest one available.
 */
class SimpleDigest
{

    private static final String[] HASH_ALGOS = new String[] { "SHA-1", "MD5" };

    private final MessageDigest digest;

    SimpleDigest()
    {
        MessageDigest md = null;
        for ( String hashAlgo : HASH_ALGOS )
        {
            try
            {
                md = MessageDigest.getInstance( hashAlgo );
                break;
            }
            catch ( NoSuchAlgorithmException ne )
            {
            }
        }
        if ( md == null )
        {
            throw new IllegalStateException( "Not supported digests: " + Arrays.toString( HASH_ALGOS ) );
        }
        this.digest = md;
    }

    public void update( String data )
    {
        if ( data == null || data.isEmpty() )
        {
            return;
        }
        digest.update( data.getBytes( StandardCharsets.UTF_8 ) );
    }

    @SuppressWarnings( "checkstyle:magicnumber" )
    public String digest()
    {
        StringBuilder buffer = new StringBuilder( 64 );

        byte[] bytes = digest.digest();
        for ( byte aByte : bytes )
        {
            int b = aByte & 0xFF;

            if ( b < 0x10 )
            {
                buffer.append( '0' );
            }

            buffer.append( Integer.toHexString( b ) );
        }

        return buffer.toString();
    }
}
