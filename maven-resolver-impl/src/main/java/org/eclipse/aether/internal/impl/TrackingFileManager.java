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
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * Manages potentially concurrent accesses to a properties file.
 */
class TrackingFileManager
{

    private static final Logger LOGGER = LoggerFactory.getLogger( TrackingFileManager.class );

    /**
     * Timeout for cached entry - 1 minute. 
     * Effectively entry is checked once per minute if it had been modified.
     */
    private static final long READ_CACHE_TIMEOUT = 60 * 1000L;

    /**
     * Caches the tracking file contents in write-through fashion. Reads are cached for READ_CACHE_TIMEOUT. Writes are
     * re-reading the file contents always - they do not rely on cached state, but they are typically much less frequent
     * than reads.
     */
    private class CacheEntry
    {

        /**
         * Tracking file
         */
        private final File file;

        /**
         * Timestamp of tracking file (last time it was read). 0 if it was never read or if file does not exist.
         */
        private long lastModifiedTs;

        /**
         * Cached properties. null if there are no properties (file does not exist) or it was not yet read
         */
        private Properties props;

        /**
         * Timestamp of last time entry was updated or read.
         */
        private long entryTs;

        CacheEntry( File file )
        {
            this.file = file;
        }

        public synchronized Properties load()
        {
            if ( hasExpired( READ_CACHE_TIMEOUT ) )
            {
                FileLock lock = null;
                FileInputStream stream = null;
                try
                {
                    if ( !file.exists() )
                    {
                        resetFile();
                    }
                    else
                    {
                        long fileLastModified = file.lastModified();
                        if ( hasFileChanged( fileLastModified ) )
                        {
                            stream = new FileInputStream( file );

                            lock = lock( stream.getChannel(), Math.max( 1, file.length() ), true );

                            props = new Properties();
                            props.load( stream );
                            lastModifiedTs = fileLastModified;
                        } // else file had not changed since last time it was read
                    }
                }
                catch ( IOException e )
                {
                    LOGGER.warn( "Failed to read tracking file {}", file, e );
                    resetFile();
                }
                finally
                {
                    release( lock );
                    close( stream );
                }
                entryTs = System.currentTimeMillis();
            }
            return props;
        }

        // checks if file last modified timestamp has changed
        private boolean hasFileChanged( long fileLastModified )
        {
            return lastModifiedTs == 0 || fileLastModified != lastModifiedTs;
        }

        // reset this entry to 'no tracking file cached' state
        private void resetFile()
        {
            props = null;
            lastModifiedTs = 0;
        }

        // checks if this entry is expired according to given timeout
        private boolean hasExpired( long timeout )
        {
            return entryTs == 0L || System.currentTimeMillis() - entryTs > timeout;
        }

        // clear any cached state of this entry
        private synchronized void expunge()
        {
            expire();
            resetFile();
        }

        // clear timestamp of this entry
        private synchronized void expire()
        {
            this.entryTs = 0L;
        }

        public synchronized Properties update( Map<String, String> updates )
        {
            props = new Properties();

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

                this.entryTs = System.currentTimeMillis();
                this.lastModifiedTs = file.lastModified();
            }
            catch ( IOException e )
            {
                LOGGER.warn( "Failed to write tracking file {}", file, e );
            }
            finally
            {
                release( lock );
                close( raf );
            }

            return props;
        }

        private void release( FileLock lock )
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

        private void close( Closeable closeable )
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

        private FileLock lock( FileChannel channel, long size, boolean shared ) throws IOException
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
                        throw (IOException) new IOException().initCause( e );
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

    /**
     * All tracking files cache entries by their canonicalized file for 5 minutes until they are released.
     * The entries are refreshed upon access (their timestamp is updated).
     */
    private static Map<File, CacheEntry> cache = Collections.synchronizedMap(new LRUCache<File, CacheEntry>(10000, 5*60*1000));

    private CacheEntry getCacheEntry( File file )
    {
        File key = getKey( file );
        CacheEntry cacheEntry = cache.get( key );
        if ( cacheEntry == null )
        {
            // no locking here - the worst case it will be created twice but only one of
            // those entries will prevail at the end.
            // since jdk8 we could use computeIfAbsent for better consistency
            cacheEntry = new CacheEntry( file );
        }
        // make sure that element's ttl is always refreshed in LRUCache
        cache.put(key, cacheEntry);
        return cacheEntry;
    }

    public Properties read( File file )
    {
        return cloneProps( getCacheEntry( file ).load() );
    }

    private Properties cloneProps( Properties ps )
    {
        return (Properties) ( ps != null ? ps.clone() : ps );
    }

    // Test only method to force cache clear
    void expunge( File file )
    {
        getCacheEntry( file ).expunge();
    }

    // Test only method to force cache expiry
    void expire( File file )
    {
        getCacheEntry( file ).expire();
    }

    public Properties update( File file, Map<String, String> updates )
    {
        return cloneProps( getCacheEntry( file ).update( updates ) );
    }

    private File getKey( File file )
    {
        /*
         * NOTE: Locks held by one JVM must not overlap and using the canonical path is our best bet, still another
         * piece of code might have locked the same file (unlikely though) or the canonical path fails to capture file
         * identity sufficiently as is the case with Java 1.6 and symlinks on Windows.
         */
        try
        {
            return canonicalize( file );
        }
        catch ( IOException e )
        {
            LOGGER.warn( "Failed to canonicalize path {}: {}", file, e.getMessage() );
            return new File( file.getAbsolutePath() );
        }
    }

    private File canonicalize( File file ) throws IOException
    {
        return file.getCanonicalFile();
    }

}
