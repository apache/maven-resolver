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

import org.eclipse.aether.SyncContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Properties;

/**
 * Manages access to a properties file. This override drops internal synchronization becauses it
 * relies on external synchronization provided by outer {@link SyncContext} instances.
 */
class TrackingFileManager
{

    private static final Logger LOGGER = LoggerFactory.getLogger( TrackingFileManager.class );

    public Properties read( File file )
    {
        FileInputStream stream = null;
        try
        {
            if ( !file.exists() )
            {
                return null;
            }

            stream = new FileInputStream( file );

            Properties props = new Properties();
            props.load( stream );

            return props;
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Failed to read tracking file {}", file, e );
        }
        finally
        {
            close( stream, file );
        }

        return null;
    }

    public Properties update( File file, Map<String, String> updates )
    {
        Properties props = new Properties();

        File directory = file.getParentFile();
        if ( !directory.mkdirs() && !directory.exists() )
        {
            LOGGER.warn( "Failed to create parent directories for tracking file {}", file );
            return props;
        }

        RandomAccessFile raf = null;
        try
        {
            raf = new RandomAccessFile( file, "rw" );

            if ( file.canRead() )
            {
                byte[] buffer = new byte[(int) raf.length()];

                raf.readFully( buffer );

                ByteArrayInputStream stream = new ByteArrayInputStream( buffer );

                props.load( stream );
            }

            for ( Map.Entry<String, String> update : updates.entrySet() )
            {
                if ( update.getValue() == null )
                {
                    props.remove( update.getKey() );
                }
                else
                {
                    props.setProperty( update.getKey(), update.getValue() );
                }
            }

            ByteArrayOutputStream stream = new ByteArrayOutputStream( 1024 * 2 );

            LOGGER.debug( "Writing tracking file {}", file );
            props.store( stream, "NOTE: This is a Maven Resolver internal implementation file"
                + ", its format can be changed without prior notice." );

            raf.seek( 0 );
            raf.write( stream.toByteArray() );
            raf.setLength( raf.getFilePointer() );
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Failed to write tracking file {}", file, e );
        }
        finally
        {
            close( raf, file );
        }

        return props;
    }

    private void close( Closeable closeable, File file )
    {
        if ( closeable != null )
        {
            try
            {
                closeable.close();
            }
            catch ( IOException e )
            {
                LOGGER.warn( "Error closing tracking file {}", file, e );
            }
        }
    }

}
