package org.eclipse.aether.internal.impl.checksum;

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

import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithm;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySupport;
import org.eclipse.aether.util.ChecksumUtils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Support class to implement {@link ChecksumAlgorithmFactory} based on Java {@link MessageDigest}.
 *
 * @since TBD
 */
public abstract class MessageDigestChecksumAlgorithmFactorySupport
        extends ChecksumAlgorithmFactorySupport
{
    public MessageDigestChecksumAlgorithmFactorySupport( String name, String extension )
    {
        super( name, extension );
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
            throw new IllegalStateException(
                    "MessageDigest algorithm " + getName() + " not supported, but is required by resolver.", e );
        }
    }
}
