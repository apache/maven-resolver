package org.eclipse.aether.named.support;

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
import java.util.concurrent.locks.ReentrantLock;

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

    private final ReentrantLock fairLock;

    public FileLockNamedLock( final String name,
                              final FileChannel fileChannel,
                              final NamedLockFactorySupport factory )
    {
        super( name, factory );
        this.threadSteps = new HashMap<>();
        this.fileChannel = fileChannel;
        this.fairLock = new ReentrantLock( true );
    }

    @Override
    public boolean lockShared( final long time, final TimeUnit unit )
    {
        fairLock.lock();
        try
        {
            Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
            if ( !steps.isEmpty() )
            { // we already own shared or exclusive lock
                logger.trace( "{} steps not empty: lock assumed", name() );
                steps.push( dummyLock( true ) );
                return true;
            }
            if ( threadSteps.size() > 1 )
            { // we may succeed (w/o locking file as JVM already hold lock) if any other thread does not have exclusive
                boolean noOtherThreadExclusive = threadSteps.values().stream()
                                                            .flatMap( Collection::stream )
                                                            .allMatch( FileLock::isShared );
                logger.trace( "{} other threads hold it: can lock = {}", name(), noOtherThreadExclusive );
                if ( noOtherThreadExclusive )
                {
                    steps.push( dummyLock( true ) );
                }
                return noOtherThreadExclusive;
            }

            logger.trace( "{} steps empty: getting real shared lock", name() );
            FileLock fileLock = realLock( true, unit.toNanos( time ) );
            if ( fileLock != null )
            {
                steps.push( fileLock );
                return true;
            }
            return false;
        }
        finally
        {
            fairLock.unlock();
        }
    }

    @Override
    public boolean lockExclusively( final long time, final TimeUnit unit )
    {
        fairLock.lock();
        try
        {
            Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
            if ( !steps.isEmpty() )
            { // we already own shared or exclusive lock
                if ( steps.stream().anyMatch( l -> !l.isShared() ) )
                {
                    logger.trace( "{} steps not empty, has exclusive lock: lock assumed", name() );
                    steps.push( dummyLock( false ) );
                    return true;
                }
                else
                {
                    logger.trace( "{} steps not empty, has not exclusive lock: lock-upgrade not supported", name() );
                    return false; // Lock upgrade not supported
                }
            }
            if ( threadSteps.size() > 1 )
            { // some other thread already posses lock, we want exclusive -> fail
                logger.trace( "{} other threads hold it: cannot lock exclusively", name() );
                return false;
            }

            logger.trace( "{} steps empty: getting real exclusive lock", name() );
            FileLock fileLock = realLock( false, unit.toNanos( time ) );
            if ( fileLock != null )
            {
                steps.push( fileLock );
                return true;
            }
            return false;
        }
        finally
        {
            fairLock.unlock();
        }
    }

    @Override
    public void unlock()
    {
        fairLock.lock();
        try
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
        finally
        {
            fairLock.unlock();
        }
    }

    private FileLock realLock( final boolean shared, final long maxWaitNanos )
    {
        boolean interrupted = false;
        long now = System.nanoTime();
        final long barrier = now + maxWaitNanos;
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
                        logger.trace( "Attempt {}: Interrupted {} while on {}",
                                attempt, Thread.currentThread().getName(), name() );
                        interrupted = Thread.interrupted();
                        break;
                    }
                }
                catch ( OverlappingFileLockException e )
                {
                    logger.trace( "Attempt {}: Overlap on {}, sleeping", attempt, name() );
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
                        logger.trace( "Attempt {}: Deadlock on {}, sleeping", attempt, name() );
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
                        logger.trace( "Attempt {}: Failure on {}", attempt, name(), e );
                        throw new UncheckedIOException( e );
                    }
                }
                now = System.nanoTime();
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