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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Adapter to adapt {@link NamedLockFactory} and {@link NamedLock} to {@link SyncContext}.
 */
public final class NamedLockFactoryAdapter
{
    private final NameMapper nameMapper;

    private final NamedLockFactory namedLockFactory;

    private final long time;

    private final TimeUnit timeUnit;

    public NamedLockFactoryAdapter( final NameMapper nameMapper, final NamedLockFactory namedLockFactory,
                                    final long time, final TimeUnit timeUnit )
    {
        this.nameMapper = Objects.requireNonNull( nameMapper );
        this.namedLockFactory = Objects.requireNonNull( namedLockFactory );
        if ( time < 0L )
        {
            throw new IllegalArgumentException( "time cannot be negative" );
        }
        this.time = time;
        this.timeUnit = Objects.requireNonNull( timeUnit );
    }

    public SyncContext newInstance( final RepositorySystemSession session, final boolean shared )
    {
        return new AdaptedLockSyncContext( session, shared, nameMapper, namedLockFactory, time, timeUnit );
    }

    public void shutdown()
    {
        namedLockFactory.shutdown();
    }

    private static class AdaptedLockSyncContext implements SyncContext
    {
        private static final Logger LOGGER = LoggerFactory.getLogger( AdaptedLockSyncContext.class );

        private final RepositorySystemSession session;

        private final boolean shared;

        private final NameMapper lockNaming;

        private final SessionAwareNamedLockFactory sessionAwareNamedLockFactory;

        private final NamedLockFactory namedLockFactory;

        private final long time;

        private final TimeUnit timeUnit;

        private final ArrayDeque<NamedLock> locks;

        private AdaptedLockSyncContext( final RepositorySystemSession session, final boolean shared,
                                        final NameMapper lockNaming, final NamedLockFactory namedLockFactory,
                                        final long time, final TimeUnit timeUnit )
        {
            this.session = session;
            this.shared = shared;
            this.lockNaming = lockNaming;
            this.sessionAwareNamedLockFactory = namedLockFactory instanceof SessionAwareNamedLockFactory
                    ? (SessionAwareNamedLockFactory) namedLockFactory : null;
            this.namedLockFactory = namedLockFactory;
            this.time = time;
            this.timeUnit = timeUnit;
            this.locks = new ArrayDeque<>();
        }

        @Override
        public void acquire( Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas )
        {
            Collection<String> keys = lockNaming.nameLocks( session, artifacts, metadatas );
            if ( keys.isEmpty() )
            {
                return;
            }

            LOGGER.trace( "Need {} {} lock(s) for {}", keys.size(), shared ? "read" : "write", keys );
            int acquiredLockCount = 0;
            for ( String key : keys )
            {
                NamedLock namedLock = sessionAwareNamedLockFactory != null ? sessionAwareNamedLockFactory
                        .getLock( session, key ) : namedLockFactory.getLock( key );
                try
                {
                     LOGGER.trace( "Acquiring {} lock for '{}'",
                             shared ? "read" : "write", key );

                    boolean locked;
                    if ( shared )
                    {
                        locked = namedLock.lockShared( time, timeUnit );
                    }
                    else
                    {
                        locked = namedLock.lockExclusively( time, timeUnit );
                    }

                    if ( !locked )
                    {
                        namedLock.close();
                        throw new IllegalStateException(
                                "Could not acquire " + ( shared ? "read" : "write" )
                                + " lock for '" + namedLock.name() + "'" );
                    }

                    locks.push( namedLock );
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
                try ( NamedLock namedLock = locks.pop() )
                {
                    LOGGER.trace( "Releasing {} lock for '{}'",
                            shared ? "read" : "write", namedLock.name() );
                    namedLock.unlock();
                    released++;
                }
            }
            LOGGER.trace( "Total locks released: {}", released );
        }
    }
}
