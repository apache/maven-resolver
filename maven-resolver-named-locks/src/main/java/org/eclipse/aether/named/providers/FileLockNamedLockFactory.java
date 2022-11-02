package org.eclipse.aether.named.providers;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.named.support.FileLockNamedLock;
import org.eclipse.aether.named.support.FileSystemFriendly;
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;
import org.eclipse.aether.named.support.Retry;

/**
 * Named locks factory of {@link FileLockNamedLock}s. This is a bit special implementation, as it
 * expects locks names to be fully qualified absolute file system paths.
 *
 * @since 1.7.3
 */
@Singleton
@Named( FileLockNamedLockFactory.NAME )
public class FileLockNamedLockFactory
    extends NamedLockFactorySupport
    implements FileSystemFriendly
{
    public static final String NAME = "file-lock";

    private static final int ATTEMPTS = Integer.parseInt(
            System.getProperty( "resolver.file-lock.attempts", "5" ) );

    private static final long SLEEP_MILLIS = Long.parseLong(
            System.getProperty( "resolver.file-lock.sleepMillis", "50" ) );

    private final ConcurrentMap<String, FileChannel> fileChannels;

    public FileLockNamedLockFactory()
    {
        this.fileChannels = new ConcurrentHashMap<>();
    }

    @Override
    protected NamedLockSupport createLock( final String name )
    {
        Path path = Paths.get( name );
        FileChannel fileChannel = fileChannels.computeIfAbsent( name, k ->
        {
            try
            {
                Files.createDirectories( path.getParent() );

                // Hack for Windows: retry multiple times and hope the best
                FileChannel channel = Retry.retry( ATTEMPTS, SLEEP_MILLIS, () ->
                {
                    try
                    {
                        return FileChannel.open(
                                path,
                                StandardOpenOption.READ, StandardOpenOption.WRITE,
                                StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE
                        );
                    }
                    catch ( AccessDeniedException e )
                    {
                        return null;
                    }
                }, null, null );

                if ( channel == null )
                {
                    throw new IllegalStateException( "Could not open file channel for '"
                            + name + "' after " + ATTEMPTS + " attempts, giving up" );
                }
                return channel;
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException( "Interrupted during open file channel for '"
                        + name + "'", e );
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( "Failed to open file channel for '"
                    + name + "'", e );
            }
        } );
        return new FileLockNamedLock( name, fileChannel, this );
    }

    @Override
    protected void destroyLock( final String name )
    {
        FileChannel fileChannel = fileChannels.remove( name );
        if ( fileChannel == null )
        {
            throw new IllegalStateException( "File channel expected, but does not exist: " + name );
        }

        try
        {
            fileChannel.close();
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( "Failed to close file channel for '"
                    + name + "'", e );
        }
    }
}
