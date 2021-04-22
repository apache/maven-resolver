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
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.support.NamedLockSupport;

/**
 * Named lock that uses {@link FileLock}.
 */
public final class FileLockNamedLock
    extends NamedLockSupport
{
    private static final long LOCK_POSITION = 0L;

    private static final long LOCK_SIZE = 1L;

    private final HashMap<Thread, Deque<FileLock>> threadSteps;

    private final FileChannel fileChannel;

    public FileLockNamedLock( final String name,
                              final FileChannel fileChannel,
                              final FileLockNamedLockFactory factory )
    {
        super( name, factory );
        this.threadSteps = new HashMap<>();
        this.fileChannel = fileChannel;
    }

    @Override
    public synchronized boolean lockShared( final long time, final TimeUnit unit )
    {
        Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
        if ( !steps.isEmpty() )
        { // we already own shared or exclusive lock
            steps.push( dummyLock( true ) );
            return true;
        }
        if ( threadSteps.size() > 1 )
        { // we may succeed (w/o locking file as JVM already hold lock) if any other thread does not have exclusive
            boolean noOtherThreadExclusive = threadSteps.values().stream()
                    .flatMap( Collection::stream )
                    .allMatch( FileLock::isShared );
            if ( noOtherThreadExclusive )
            {
                steps.push( dummyLock( true ) );
            }
            return noOtherThreadExclusive;
        }

        FileLock fileLock = realLock( true, unit.toMillis( time ) );
        if ( fileLock != null )
        {
            steps.push( fileLock );
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean lockExclusively( final long time, final TimeUnit unit )
    {
        Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
        if ( !steps.isEmpty() )
        { // we already own shared or exclusive lock
            if ( steps.stream().anyMatch( l -> !l.isShared() ) )
            {
                steps.push( dummyLock( false ) );
                return true;
            }
            else
            {
                return false; // Lock upgrade not supported
            }
        }
        if ( threadSteps.size() > 1 )
        { // some other thread already posses lock, we want exclusive -> fail
            return false;
        }

        FileLock fileLock = realLock( false, unit.toMillis( time ) );
        if ( fileLock != null )
        {
            steps.push( fileLock );
            return true;
        }
        return false;
    }

    @Override
    public synchronized void unlock()
    {
        Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
        if ( steps.isEmpty() )
        {
            throw new IllegalStateException( "Wrong API usage: unlock without lock" );
        }
        try
        {
            steps.pop().release();
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }

    private FileLock realLock( final boolean shared, final long maxWaitMillis )
    {
        boolean interrupted = false;
        long now = System.currentTimeMillis();
        final long barrier = now + maxWaitMillis;
        FileLock result = null;
        int attempt = 1;
        try
        {
            while ( now < barrier && result == null )
            {
                try
                {
                    result = fileChannel.tryLock( LOCK_POSITION, LOCK_SIZE, shared );
                    if ( result == null )
                    {
                        logger.trace( "Interrupted {} while on {}", Thread.currentThread().getName(), name() );
                        interrupted = Thread.interrupted();
                        break;
                    }
                }
                catch ( OverlappingFileLockException e )
                {
                    logger.trace( "Overlap on {}, sleeping", name() );
                    try
                    {
                        Thread.sleep( 100 );
                    }
                    catch ( InterruptedException intEx )
                    {
                        interrupted = true;
                        break;
                    }
                }
                catch ( IOException e )
                {
                    if ( e.getMessage().toLowerCase( Locale.ENGLISH ).contains( "deadlock" ) )
                    {
                        logger.trace( "Deadlock on {}, sleeping", name() );
                        try
                        {
                            Thread.sleep( 100 );
                        }
                        catch ( InterruptedException intEx )
                        {
                            interrupted = true;
                            break;
                        }
                    }
                    else
                    {
                        logger.trace( "Failure on {}", name(), e );
                        throw new UncheckedIOException( e );
                    }
                }
                now = System.currentTimeMillis();
                attempt++;
            }
        }
        finally
        {
            if ( interrupted )
            {
                logger.trace( "Interrupted thread {} while in lock {}", Thread.currentThread().getName(), name() );
                Thread.currentThread().interrupt();
            }

        }
        return result;
    }

    private FileLock dummyLock( final boolean shared )
    {
        return new DummyLock( fileChannel, shared );
    }

    private static final class DummyLock extends FileLock
    {
        private DummyLock( final FileChannel channel, final boolean shared )
        {
            super( channel, LOCK_POSITION, LOCK_SIZE, shared );
        }

        @Override
        public boolean isValid()
        {
            return channel().isOpen();
        }

        @Override
        public void release() throws IOException
        {
            // noop
        }
    }
}
