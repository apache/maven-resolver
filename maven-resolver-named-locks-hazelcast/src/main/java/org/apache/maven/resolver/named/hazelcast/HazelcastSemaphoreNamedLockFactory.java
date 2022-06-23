package org.apache.maven.resolver.named.hazelcast;

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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.ISemaphore;
import org.apache.maven.resolver.named.support.NamedLockFactorySupport;
import org.apache.maven.resolver.named.support.AdaptedSemaphoreNamedLock;
import org.apache.maven.resolver.named.support.AdaptedSemaphoreNamedLock.AdaptedSemaphore;

import static java.util.Objects.requireNonNull;

/**
 * Factory of {@link AdaptedSemaphoreNamedLock} instances, using adapted Hazelcast {@link ISemaphore}. It delegates
 * most the work to {@link HazelcastSemaphoreProvider} and this class just adapts the returned semaphore to named lock
 * and caches {@link ISemaphore} instances, as recommended by Hazelcast.
 */
public class HazelcastSemaphoreNamedLockFactory
        extends NamedLockFactorySupport
{
    protected final HazelcastInstance hazelcastInstance;

    protected final boolean manageHazelcast;

    private final HazelcastSemaphoreProvider hazelcastSemaphoreProvider;

    private final ConcurrentMap<String, ISemaphore> semaphores;

    public HazelcastSemaphoreNamedLockFactory(
            final HazelcastInstance hazelcastInstance,
            final boolean manageHazelcast,
            final HazelcastSemaphoreProvider hazelcastSemaphoreProvider
    )
    {
        this.hazelcastInstance = requireNonNull( hazelcastInstance );
        this.manageHazelcast = manageHazelcast;
        this.hazelcastSemaphoreProvider = requireNonNull( hazelcastSemaphoreProvider );
        this.semaphores = new ConcurrentHashMap<>();
    }

    @Override
    protected AdaptedSemaphoreNamedLock createLock( final String name )
    {
        ISemaphore semaphore = semaphores.computeIfAbsent( name,
                k -> hazelcastSemaphoreProvider.acquireSemaphore( hazelcastInstance, name ) );
        return new AdaptedSemaphoreNamedLock( name, this, new HazelcastSemaphore( semaphore ) );
    }

    @Override
    protected void destroyLock( final String name )
    {
        hazelcastSemaphoreProvider.releaseSemaphore( hazelcastInstance, name, semaphores.remove( name ) );
    }

    @Override
    public void shutdown()
    {
        if ( manageHazelcast )
        {
            hazelcastInstance.shutdown();
        }
    }

    private static final class HazelcastSemaphore implements AdaptedSemaphore
    {
        private final ISemaphore semaphore;

        private HazelcastSemaphore( final ISemaphore semaphore )
        {
            this.semaphore = semaphore;
        }

        @Override
        public boolean tryAcquire( final int perms, final long time, final TimeUnit unit )
                throws InterruptedException
        {
            return semaphore.tryAcquire( perms, time, unit );
        }

        @Override
        public void release( final int perms )
        {
            semaphore.release( perms );
        }
    }
}
