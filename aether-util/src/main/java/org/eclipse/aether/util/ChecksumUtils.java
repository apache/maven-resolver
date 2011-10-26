/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A utility class to assist in the verification and generation of checksums.
 */
public class ChecksumUtils
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

        FileInputStream fis = new FileInputStream( checksumFile );
        try
        {
            BufferedReader br = new BufferedReader( new InputStreamReader( fis, "UTF-8" ) );
            try
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
            finally
            {
                try
                {
                    br.close();
                }
                catch ( IOException e )
                {
                    // ignored
                }
            }
        }
        finally
        {
            try
            {
                fis.close();
            }
            catch ( IOException e )
            {
                // ignored
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
        Map<String, Object> results = new LinkedHashMap<String, Object>();

        Map<String, MessageDigest> digests = new LinkedHashMap<String, MessageDigest>();
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

        FileInputStream fis = new FileInputStream( dataFile );
        try
        {
            for ( byte[] buffer = new byte[32 * 1024];; )
            {
                int read = fis.read( buffer );
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
        finally
        {
            try
            {
                fis.close();
            }
            catch ( IOException e )
            {
                // ignored
            }
        }

        for ( Map.Entry<String, MessageDigest> entry : digests.entrySet() )
        {
            byte[] bytes = entry.getValue().digest();

            results.put( entry.getKey(), toHexString( bytes ) );
        }

        return results;
    }

    private static String toHexString( byte[] bytes )
    {
        StringBuilder buffer = new StringBuilder( bytes.length * 2 );

        for ( int i = 0; i < bytes.length; i++ )
        {
            int b = bytes[i] & 0xFF;
            if ( b < 0x10 )
            {
                buffer.append( '0' );
            }
            buffer.append( Integer.toHexString( b ) );
        }

        return buffer.toString();
    }

}
