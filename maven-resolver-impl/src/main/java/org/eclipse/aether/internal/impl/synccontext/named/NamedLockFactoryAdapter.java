package org.eclipse.aether.internal.impl.synccontext.named;

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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Adapter to adapt {@link NamedLockFactory} and {@link NamedLock} to {@link SyncContext}.
 */
public final class NamedLockFactoryAdapter
{
    private final ResolverLockFactory resolverLockFactory;

    private final long time;

    private final TimeUnit timeUnit;

    public NamedLockFactoryAdapter( final ResolverLockFactory resolverLockFactory,
                                    final long time, final TimeUnit timeUnit )
    {
        this.resolverLockFactory = resolverLockFactory;
        if ( time < 0L )
        {
            throw new IllegalArgumentException( "time cannot be negative" );
        }
        this.time = time;
        this.timeUnit = Objects.requireNonNull( timeUnit );
    }

    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        return new AdaptedLockSyncContext( session, shared, resolverLockFactory, time, timeUnit );
    }

    public void shutdown()
    {
        resolverLockFactory.shutdown();
    }

    private static class AdaptedLockSyncContext implements SyncContext
    {
        private static final Logger LOGGER = LoggerFactory.getLogger( AdaptedLockSyncContext.class );

        private final RepositorySystemSession session;

        private final boolean shared;

        private final ResolverLockFactory resolverLockFactory;

        private final long time;

        private final TimeUnit timeUnit;

        private final Deque<ResolverLock> locks;

        private AdaptedLockSyncContext( final RepositorySystemSession session, final boolean shared,
                                        final ResolverLockFactory resolverLockFactory,
                                        final long time, final TimeUnit timeUnit )
        {
            this.session = session;
            this.shared = shared;
            this.resolverLockFactory = resolverLockFactory;
            this.time = time;
            this.timeUnit = timeUnit;
            this.locks = new ArrayDeque<>();
        }

        @Override
        public void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas )
        {
            Collection<ResolverLock> resolverLocks = resolverLockFactory.resolverLocks(
                    session, shared, artifacts, metadatas );
            if ( resolverLocks.isEmpty() )
            {
                return;
            }

            LOGGER.trace( "Need {} {} lock(s) for {}", resolverLocks.size(), shared ? "read" : "write", resolverLocks );
            int acquiredLockCount = 0;
            for ( ResolverLock resolverLock : resolverLocks )
            {
                try
                {
                     LOGGER.trace( "Acquiring effective {} lock for '{}'",
                             resolverLock.isEffectiveShared() ? "read" : "write", resolverLock.key() );

                    boolean locked = resolverLock.tryLock( time, timeUnit );
                    if ( !locked )
                    {
                        LOGGER.trace( "Failed to acquire effective {} lock for '{}'",
                                resolverLock.isEffectiveShared() ? "read" : "write", resolverLock.key() );

                        resolverLock.close();
                        throw new IllegalStateException(
                                "Could not acquire effective " + ( resolverLock.isEffectiveShared() ? "read" : "write" )
                                + " lock for '" + resolverLock.key() + "'" );
                    }

                    locks.push( resolverLock );
                    acquiredLockCount++;
                }
                catch ( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException( e );
                }
            }
            LOGGER.trace( "Total locks acquired: {}", acquiredLockCount );
        }

        @Override
        public void close()
        {
            if ( locks.isEmpty() )
            {
                return;
            }

            // Release locks in reverse insertion order
            int released = 0;
            while ( !locks.isEmpty() )
            {
                try ( ResolverLock resolverLock = locks.pop() )
                {
                    LOGGER.trace( "Releasing {} lock for '{}'",
                            shared ? "read" : "write", resolverLock.key() );
                    resolverLock.unlock();
                    released++;
                }
            }
            LOGGER.trace( "Total locks released: {}", released );
        }
    }
}
