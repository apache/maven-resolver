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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.internal.impl.synccontext.NamedLockFactorySelector;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory.TIME;
import static org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory.TIME_UNIT;

/**
 * Manages access to a properties file.
 */
@Singleton
@Named
public final class DefaultTrackingFileManager
    implements TrackingFileManager, Service
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultTrackingFileManager.class );

    private static final String LOCK_PREFIX = "tracking:";

    private NamedLockFactory namedLockFactory;

    public DefaultTrackingFileManager()
    {
        // ctor for ServiceLocator
    }

    @Inject
    public DefaultTrackingFileManager( final NamedLockFactorySelector selector )
    {
        this.namedLockFactory = selector.getSelected();
    }

    @Override
    public void initService( final ServiceLocator locator )
    {
        NamedLockFactorySelector select = Objects.requireNonNull(
            locator.getService( NamedLockFactorySelector.class ) );
        this.namedLockFactory = select.getSelected();
    }

    private String getFileKey( final File file )
    {
        return LOCK_PREFIX + "foo"; // hash file abs path?
    }

    @Override
    public Properties read( File file )
    {
        try ( NamedLock lock = namedLockFactory.getLock( getFileKey( file ) ) )
        {
            if ( lock.lockShared( TIME, TIME_UNIT ) )
            {
                try
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
                finally
                {
                    lock.unlock();
                }
            }
            else
            {
                throw new IllegalStateException( "Could not acquire shared lock for: " + file );
            }
        }
        catch ( InterruptedException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public Properties update( File file, Map<String, String> updates )
    {
        try ( NamedLock lock = namedLockFactory.getLock( getFileKey( file ) ) )
        {
            if ( lock.lockExclusively( TIME, TIME_UNIT ) )
            {
                try
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
                finally
                {
                    lock.unlock();
                }
            }
            else
            {
                throw new IllegalStateException( "Could not acquire shared lock for: " + file );
            }
        }
        catch ( InterruptedException e )
        {
            throw new IllegalStateException( e );
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

}
