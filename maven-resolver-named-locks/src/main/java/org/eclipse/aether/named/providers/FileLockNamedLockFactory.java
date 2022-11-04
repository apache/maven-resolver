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
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;

import static org.eclipse.aether.named.support.Retry.retry;

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
{
    public static final String NAME = "file-lock";

    /**
     * Tweak: on Windows, the presence of {@link StandardOpenOption#DELETE_ON_CLOSE} causes concurrency issues. This
     * flag allows to have it removed from effective flags, at the cost that lockfile directory becomes crowded
     * with 0 byte sized lock files that are never cleaned up.
     *
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8252883">JDK-8252883</a>
     */
    private static final boolean DELETE_LOCK_FILES = Boolean.parseBoolean(
            System.getProperty( "aether.named.file-lock.deleteLockFiles", Boolean.TRUE.toString() ) );

    /**
     * Tweak: on Windows, the presence of {@link StandardOpenOption#DELETE_ON_CLOSE} causes concurrency issues. This
     * flag allows to implement similar fix as referenced JDK bug report: retry and hope the best. Default value is
     * 5 attempts (will retry 4 times).
     *
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8252883">JDK-8252883</a>
     */
    private static final int ATTEMPTS = Integer.parseInt(
            System.getProperty( "aether.named.file-lock.attempts", "5" ) );

    /**
     * Tweak: When {@link #ATTEMPTS} used, the amount of milliseconds to sleep between subsequent retries.
     */
    private static final long SLEEP_MILLIS = Long.parseLong(
            System.getProperty( "aether.named.file-lock.sleepMillis", "50" ) );

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
                FileChannel channel = retry( ATTEMPTS, SLEEP_MILLIS, () ->
                {
                    try
                    {
                        if ( DELETE_LOCK_FILES )
                        {
                            return FileChannel.open(
                                    path,
                                    StandardOpenOption.READ, StandardOpenOption.WRITE,
                                    StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE
                            );
                        }
                        else
                        {
                            return FileChannel.open(
                                    path,
                                    StandardOpenOption.READ, StandardOpenOption.WRITE,
                                    StandardOpenOption.CREATE
                            );
                        }
                    }
                    catch ( AccessDeniedException e )
                    {
                        return null;
                    }
                }, null, null );

                if ( channel == null )
                {
                    throw new IllegalStateException( "Could not open file channel for '"
                            + name + "' after " + ATTEMPTS + " attempts; giving up" );
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
