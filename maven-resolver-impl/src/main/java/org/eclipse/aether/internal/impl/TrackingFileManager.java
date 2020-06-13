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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Map;
import java.util.Properties;

/**
 * Manages potentially concurrent accesses to a properties file.
 */
class TrackingFileManager
{

    private static final Logger LOGGER = LoggerFactory.getLogger( TrackingFileManager.class );

    public Properties read( File file )
    {
        synchronized ( getLock( file ) )
        {
            FileLock lock = null;
            FileInputStream stream = null;
            try
            {
                if ( !file.exists() )
                {
                    return null;
                }

                stream = new FileInputStream( file );

                lock = lock( stream.getChannel(), Math.max( 1, file.length() ), true );

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
                release( lock, file );
                close( stream, file );
            }
        }

        return null;
    }

    public Properties update( File file, Map<String, String> updates )
    {
        Properties props = new Properties();

        synchronized ( getLock( file ) )
        {
            File directory = file.getParentFile();
            if ( !directory.mkdirs() && !directory.exists() )
            {
                LOGGER.warn( "Failed to create parent directories for tracking file {}", file );
                return props;
            }

            RandomAccessFile raf = null;
            FileLock lock = null;
            try
            {
                raf = new RandomAccessFile( file, "rw" );
                lock = lock( raf.getChannel(), Math.max( 1, raf.length() ), false );

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
                release( lock, file );
                close( raf, file );
            }
        }

        return props;
    }

    private void release( FileLock lock, File file )
    {
        if ( lock != null )
        {
            try
            {
                lock.release();
            }
            catch ( IOException e )
            {
                LOGGER.warn( "Error releasing lock for tracking file {}", file, e );
            }
        }
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

    private Object getLock( File file )
    {
        /*
         * NOTE: Locks held by one JVM must not overlap and using the canonical path is our best bet, still another
         * piece of code might have locked the same file (unlikely though) or the canonical path fails to capture file
         * identity sufficiently as is the case with Java 1.6 and symlinks on Windows.
         */
        try
        {
            return file.getCanonicalPath().intern();
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Failed to canonicalize path {}", file, e );
            // TODO This is code smell and deprecated
            return file.getAbsolutePath().intern();
        }
    }

    @SuppressWarnings( { "checkstyle:magicnumber" } )
    private FileLock lock( FileChannel channel, long size, boolean shared )
        throws IOException
    {
        FileLock lock = null;

        for ( int attempts = 8; attempts >= 0; attempts-- )
        {
            try
            {
                lock = channel.lock( 0, size, shared );
                break;
            }
            catch ( OverlappingFileLockException e )
            {
                if ( attempts <= 0 )
                {
                    throw new IOException( e );
                }
                try
                {
                    Thread.sleep( 50L );
                }
                catch ( InterruptedException e1 )
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if ( lock == null )
        {
            throw new IOException( "Could not lock file" );
        }

        return lock;
    }

}
