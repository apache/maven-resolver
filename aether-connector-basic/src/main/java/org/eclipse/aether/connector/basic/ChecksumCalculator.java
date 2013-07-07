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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.util.ChecksumUtils;

/**
 * Calculates checksums for downloaded files.
 */
final class ChecksumCalculator
{

    private final Map<String, MessageDigest> digests;

    private final File targetFile;

    private Exception targetFileError;

    public static ChecksumCalculator newInstance( File targetFile, Collection<RepositoryLayout.Checksum> checksums )
    {
        if ( checksums == null || checksums.isEmpty() )
        {
            return null;
        }
        return new ChecksumCalculator( targetFile, checksums );
    }

    private ChecksumCalculator( File targetFile, Collection<RepositoryLayout.Checksum> checksums )
    {
        digests = new HashMap<String, MessageDigest>();
        for ( RepositoryLayout.Checksum checksum : checksums )
        {
            String algo = checksum.getAlgorithm();
            try
            {
                if ( !digests.containsKey( algo ) )
                {
                    MessageDigest digest = MessageDigest.getInstance( algo );
                    digests.put( algo, digest );
                }
            }
            catch ( NoSuchAlgorithmException e )
            {
                // treat this checksum as missing
            }
        }
        this.targetFile = targetFile;
    }

    public void init( long dataOffset )
    {
        for ( MessageDigest digest : digests.values() )
        {
            digest.reset();
        }
        targetFileError = null;
        if ( dataOffset <= 0 )
        {
            return;
        }
        try
        {
            FileInputStream fis = new FileInputStream( targetFile );
            try
            {
                long total = 0;
                for ( byte[] buffer = new byte[32 * 1024]; total < dataOffset; )
                {
                    int read = fis.read( buffer );
                    if ( read < 0 )
                    {
                        if ( total < dataOffset )
                        {
                            throw new IOException( targetFile + " contains only " + total
                                + " bytes, cannot resume download from offset " + dataOffset );
                        }
                        break;
                    }
                    total += read;
                    if ( total > dataOffset )
                    {
                        read -= total - dataOffset;
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
                    // irrelevant
                }
            }
        }
        catch ( IOException e )
        {
            targetFileError = e;
        }
    }

    public void update( ByteBuffer data )
    {
        for ( MessageDigest digest : digests.values() )
        {
            data.mark();
            digest.update( data );
            data.reset();
        }
    }

    public Map<String, Object> get()
    {
        Map<String, Object> results = new HashMap<String, Object>();
        if ( targetFileError != null )
        {
            for ( String algo : digests.keySet() )
            {
                results.put( algo, targetFileError );
            }
        }
        else
        {
            for ( Map.Entry<String, MessageDigest> entry : digests.entrySet() )
            {
                results.put( entry.getKey(), ChecksumUtils.toHexString( entry.getValue().digest() ) );
            }
        }
        return results;
    }

}
