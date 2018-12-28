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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A utility class to assist in the verification and generation of checksums.
 */
public final class ChecksumUtils
{

    private ChecksumUtils()
    {
        // hide constructor
    }

    /**
     * Extracts the checksum from the specified file.
     * 
     * @param checksumFile The path to the checksum file, must not be {@code null}.
     * @return The checksum stored in the file, never {@code null}.
     * @throws IOException If the checksum does not exist or could not be read for other reasons.
     */
    public static String read( File checksumFile )
        throws IOException
    {
        String checksum = "";
        try ( BufferedReader br = new BufferedReader( new InputStreamReader(
                new FileInputStream( checksumFile ), StandardCharsets.UTF_8 ), 512 ) )
        {
            while ( true )
            {
                String line = br.readLine();
                if ( line == null )
                {
                    break;
                }
                line = line.trim();
                if ( line.length() > 0 )
                {
                    checksum = line;
                    break;
                }
            }
        }

        if ( checksum.matches( ".+= [0-9A-Fa-f]+" ) )
        {
            int lastSpacePos = checksum.lastIndexOf( ' ' );
            checksum = checksum.substring( lastSpacePos + 1 );
        }
        else
        {
            int spacePos = checksum.indexOf( ' ' );

            if ( spacePos != -1 )
            {
                checksum = checksum.substring( 0, spacePos );
            }
        }

        return checksum;
    }

    /**
     * Calculates checksums for the specified file.
     * 
     * @param dataFile The file for which to calculate checksums, must not be {@code null}.
     * @param algos The names of checksum algorithms (cf. {@link MessageDigest#getInstance(String)} to use, must not be
     *            {@code null}.
     * @return The calculated checksums, indexed by algorithm name, or the exception that occurred while trying to
     *         calculate it, never {@code null}.
     * @throws IOException If the data file could not be read.
     */
    public static Map<String, Object> calc( File dataFile, Collection<String> algos )
                    throws IOException
    {
       return calc( new FileInputStream( dataFile ), algos );
    }

    
    public static Map<String, Object> calc( byte[] dataBytes, Collection<String> algos )
                    throws IOException
    {
        return calc( new ByteArrayInputStream( dataBytes ), algos );
    }

    
    private static Map<String, Object> calc( InputStream data, Collection<String> algos )
        throws IOException
    {
        Map<String, Object> results = new LinkedHashMap<>();

        Map<String, MessageDigest> digests = new LinkedHashMap<>();
        for ( String algo : algos )
        {
            try
            {
                digests.put( algo, MessageDigest.getInstance( algo ) );
            }
            catch ( NoSuchAlgorithmException e )
            {
                results.put( algo, e );
            }
        }

        try ( InputStream in = data )
        {
            for ( byte[] buffer = new byte[ 32 * 1024 ];; )
            {
                int read = in.read( buffer );
                if ( read < 0 )
                {
                    break;
                }
                for ( MessageDigest digest : digests.values() )
                {
                    digest.update( buffer, 0, read );
                }
            }
        }

        for ( Map.Entry<String, MessageDigest> entry : digests.entrySet() )
        {
            byte[] bytes = entry.getValue().digest();

            results.put( entry.getKey(), toHexString( bytes ) );
        }

        return results;
    }
    

    /**
     * Creates a hexadecimal representation of the specified bytes. Each byte is converted into a two-digit hex number
     * and appended to the result with no separator between consecutive bytes.
     * 
     * @param bytes The bytes to represent in hex notation, may be be {@code null}.
     * @return The hexadecimal representation of the input or {@code null} if the input was {@code null}.
     */
    @SuppressWarnings( "checkstyle:magicnumber" )
    public static String toHexString( byte[] bytes )
    {
        if ( bytes == null )
        {
            return null;
        }

        StringBuilder buffer = new StringBuilder( bytes.length * 2 );

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
