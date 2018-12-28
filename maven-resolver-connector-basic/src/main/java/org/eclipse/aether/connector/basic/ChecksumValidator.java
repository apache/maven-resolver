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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout.Checksum;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.util.ChecksumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger( ChecksumValidator.class );

    private final File dataFile;

    private final Collection<File> tempFiles;

    private final FileProcessor fileProcessor;

    private final ChecksumFetcher checksumFetcher;

    private final ChecksumPolicy checksumPolicy;

    private final Collection<Checksum> checksums;

    private final Map<File, Object> checksumFiles;

    ChecksumValidator( File dataFile, FileProcessor fileProcessor,
                              ChecksumFetcher checksumFetcher, ChecksumPolicy checksumPolicy,
                              Collection<Checksum> checksums )
    {
        this.dataFile = dataFile;
        this.tempFiles = new HashSet<>();
        this.fileProcessor = fileProcessor;
        this.checksumFetcher = checksumFetcher;
        this.checksumPolicy = checksumPolicy;
        this.checksums = checksums;
        checksumFiles = new HashMap<>();
    }

    public ChecksumCalculator newChecksumCalculator( File targetFile )
    {
        if ( checksumPolicy != null )
        {
            return ChecksumCalculator.newInstance( targetFile, checksums );
        }
        return null;
    }

    public void validate( Map<String, ?> actualChecksums, Map<String, ?> inlinedChecksums )
        throws ChecksumFailureException
    {
        if ( checksumPolicy == null )
        {
            return;
        }
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

    private boolean validateInlinedChecksums( Map<String, ?> actualChecksums, Map<String, ?> inlinedChecksums )
        throws ChecksumFailureException
    {
        for ( Map.Entry<String, ?> entry : inlinedChecksums.entrySet() )
        {
            String algo = entry.getKey();
            Object calculated = actualChecksums.get( algo );
            if ( !( calculated instanceof String ) )
            {
                continue;
            }

            String actual = String.valueOf( calculated );
            String expected = entry.getValue().toString();
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

    private boolean validateExternalChecksums( Map<String, ?> actualChecksums )
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
            File.createTempFile( path.getName() + "-"
                + UUID.randomUUID().toString().replace( "-", "" ).substring( 0, 8 ), ".tmp", path.getParentFile() );
        tempFiles.add( file );
        return file;
    }

    private void clearTempFiles()
    {
        for ( File file : tempFiles )
        {
            if ( !file.delete() && file.exists() )
            {
                LOGGER.debug( "Could not delete temporary file {}", file );
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
                LOGGER.debug( "Failed to write checksum file {}: {}", checksumFile, e.getMessage(), e );
            }
        }
        checksumFiles.clear();
    }

    public void close()
    {
        clearTempFiles();
    }

}
