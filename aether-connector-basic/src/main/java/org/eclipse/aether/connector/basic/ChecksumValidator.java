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
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.util.ChecksumUtils;

/**
 * Performs checksum validation for a downloaded file.
 */
final class ChecksumValidator
{

    interface ChecksumFetcher
    {

        boolean fetchChecksum( URI remote, File local )
            throws Exception;

    }

    private final Logger logger;

    private final File dataFile;

    private final Collection<File> tempFiles;

    private final FileProcessor fileProcessor;

    private final ChecksumFetcher checksumFetcher;

    private final ChecksumPolicy checksumPolicy;

    private final Collection<Checksum> checksums;

    private final Map<File, Object> checksumFiles;

    private ChecksumCalculator checksumCalculator;

    public ChecksumValidator( Logger logger, File dataFile, FileProcessor fileProcessor,
                              ChecksumFetcher checksumFetcher, ChecksumPolicy checksumPolicy,
                              Collection<Checksum> checksums )
    {
        this.logger = logger;
        this.dataFile = dataFile;
        this.tempFiles = new HashSet<File>();
        this.fileProcessor = fileProcessor;
        this.checksumFetcher = checksumFetcher;
        this.checksumPolicy = checksumPolicy;
        this.checksums = checksums;
        checksumFiles = new HashMap<File, Object>();
    }

    public ChecksumCalculator init( File targetFile )
    {
        if ( checksumPolicy != null )
        {
            checksumCalculator = ChecksumCalculator.newInstance( targetFile, checksums );
        }
        return checksumCalculator;
    }

    public void validate( Map<String, String> inlinedChecksums )
        throws ChecksumFailureException
    {
        if ( checksumPolicy == null )
        {
            return;
        }
        Map<String, Object> actualChecksums =
            ( checksumCalculator != null ) ? checksumCalculator.get() : Collections.<String, Object> emptyMap();
        if ( inlinedChecksums != null && validateInlinedChecksums( actualChecksums, inlinedChecksums ) )
        {
            return;
        }
        if ( validateExternalChecksums( actualChecksums ) )
        {
            return;
        }
        checksumPolicy.onNoMoreChecksums();
    }

    private boolean validateInlinedChecksums( Map<String, Object> actualChecksums, Map<String, String> inlinedChecksums )
        throws ChecksumFailureException
    {
        for ( Map.Entry<String, String> entry : inlinedChecksums.entrySet() )
        {
            String algo = entry.getKey();
            Object calculated = actualChecksums.get( algo );
            if ( !( calculated instanceof String ) )
            {
                continue;
            }

            String actual = String.valueOf( calculated );
            String expected = entry.getValue();
            checksumFiles.put( getChecksumFile( algo ), expected );

            if ( !isEqualChecksum( expected, actual ) )
            {
                checksumPolicy.onChecksumMismatch( algo, ChecksumPolicy.KIND_UNOFFICIAL,
                                                   new ChecksumFailureException( expected, actual ) );
            }
            else if ( checksumPolicy.onChecksumMatch( algo, ChecksumPolicy.KIND_UNOFFICIAL ) )
            {
                return true;
            }
        }
        return false;
    }

    private boolean validateExternalChecksums( Map<String, Object> actualChecksums )
        throws ChecksumFailureException
    {
        for ( Checksum checksum : checksums )
        {
            String algo = checksum.getAlgorithm();
            Object calculated = actualChecksums.get( algo );
            if ( calculated instanceof Exception )
            {
                checksumPolicy.onChecksumError( algo, 0, new ChecksumFailureException( (Exception) calculated ) );
                continue;
            }
            try
            {
                File checksumFile = getChecksumFile( checksum.getAlgorithm() );
                File tmp = createTempFile( checksumFile );
                try
                {
                    if ( !checksumFetcher.fetchChecksum( checksum.getLocation(), tmp ) )
                    {
                        continue;
                    }
                }
                catch ( Exception e )
                {
                    checksumPolicy.onChecksumError( algo, 0, new ChecksumFailureException( e ) );
                    continue;
                }

                String actual = String.valueOf( calculated );
                String expected = ChecksumUtils.read( tmp );
                checksumFiles.put( checksumFile, tmp );

                if ( !isEqualChecksum( expected, actual ) )
                {
                    checksumPolicy.onChecksumMismatch( algo, 0, new ChecksumFailureException( expected, actual ) );
                }
                else if ( checksumPolicy.onChecksumMatch( algo, 0 ) )
                {
                    return true;
                }
            }
            catch ( IOException e )
            {
                checksumPolicy.onChecksumError( algo, 0, new ChecksumFailureException( e ) );
            }
        }
        return false;
    }

    private static boolean isEqualChecksum( String expected, String actual )
    {
        return expected.equalsIgnoreCase( actual );
    }

    private File getChecksumFile( String algorithm )
    {
        String ext = algorithm.replace( "-", "" ).toLowerCase( Locale.ENGLISH );
        return new File( dataFile.getPath() + '.' + ext );
    }

    private File createTempFile( File path )
        throws IOException
    {
        File file =
            File.createTempFile( path.getName() + "-" + UUID.randomUUID().toString().replace( "-", "" ), ".tmp",
                                 path.getParentFile() );
        tempFiles.add( file );
        return file;
    }

    private void clearTempFiles()
    {
        for ( File file : tempFiles )
        {
            if ( !file.delete() && file.exists() )
            {
                logger.debug( "Could not delete temorary file " + file );
            }
        }
        tempFiles.clear();
    }

    public void retry()
    {
        checksumPolicy.onTransferRetry();
        checksumFiles.clear();
        clearTempFiles();
    }

    public boolean handle( ChecksumFailureException exception )
    {
        return checksumPolicy.onTransferChecksumFailure( exception );
    }

    public void commit()
    {
        for ( Map.Entry<File, Object> entry : checksumFiles.entrySet() )
        {
            File checksumFile = entry.getKey();
            Object tmp = entry.getValue();
            try
            {
                if ( tmp instanceof File )
                {
                    fileProcessor.move( (File) tmp, checksumFile );
                    tempFiles.remove( tmp );
                }
                else
                {
                    fileProcessor.write( checksumFile, String.valueOf( tmp ) );
                }
            }
            catch ( IOException e )
            {
                logger.debug( "Failed to write checksum file " + checksumFile + ": " + e.getMessage(), e );
            }
        }
        checksumFiles.clear();
    }

    public void close()
    {
        clearTempFiles();
    }

}
