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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
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
        try
        {
            FileLock fileLock = fileChannel.tryLock( LOCK_POSITION, LOCK_SIZE, true );
            if ( fileLock != null )
            {
                steps.push( fileLock );
                return true;
            }
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
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
        try
        {
            FileLock fileLock = fileChannel.tryLock( LOCK_POSITION, LOCK_SIZE, false );
            if ( fileLock != null )
            {
                steps.push( fileLock );
                return true;
            }
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
        return false;
    }

    @Override
    public synchronized void unlock()
    {
        Deque<FileLock> steps = threadSteps.computeIfAbsent( Thread.currentThread(), k -> new ArrayDeque<>() );
        if ( steps.isEmpty() )
        {
            throw new IllegalStateException( "Wrong API usage: unlock w/o lock" );
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
