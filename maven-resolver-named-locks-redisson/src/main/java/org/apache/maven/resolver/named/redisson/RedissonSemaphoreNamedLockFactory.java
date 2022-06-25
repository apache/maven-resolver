package org.apache.maven.resolver.named.redisson;

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

import org.apache.maven.resolver.named.support.AdaptedSemaphoreNamedLock;
import org.redisson.api.RSemaphore;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Provider of {@link RedissonSemaphoreNamedLockFactory} using Redisson and {@link org.redisson.api.RSemaphore}.
 */
@Singleton
@Named( RedissonSemaphoreNamedLockFactory.NAME )
public class RedissonSemaphoreNamedLockFactory
    extends RedissonNamedLockFactorySupport
{
    public static final String NAME = "semaphore-redisson";

    private static final String TYPED_NAME_PREFIX = NAME_PREFIX + NAME + ":";

    private final ConcurrentMap<String, RSemaphore> semaphores;

    public RedissonSemaphoreNamedLockFactory()
    {
        this.semaphores = new ConcurrentHashMap<>();
    }

    @Override
    protected AdaptedSemaphoreNamedLock createLock( final String name )
    {
        RSemaphore semaphore = semaphores.computeIfAbsent( name, k ->
        {
            RSemaphore result = redissonClient.getSemaphore( TYPED_NAME_PREFIX + k );
            result.trySetPermits( Integer.MAX_VALUE );
            return result;
        } );
        return new AdaptedSemaphoreNamedLock( name, this, new RedissonSemaphore( semaphore ) );
    }

    @Override
    protected void destroyLock( final String name )
    {
        RSemaphore semaphore = semaphores.remove( name );
        if ( semaphore == null )
        {
            throw new IllegalStateException( "Semaphore expected, but does not exist: " + name );
        }
        /* Threre is no reasonable way to destroy the semaphore in Redis because we cannot know
         * when the last process has stopped using it.
         */
    }

    private static final class RedissonSemaphore implements AdaptedSemaphoreNamedLock.AdaptedSemaphore
    {
        private final RSemaphore semaphore;

        private RedissonSemaphore( final RSemaphore semaphore )
        {
            this.semaphore = semaphore;
        }

        @Override
        public boolean tryAcquire( final int perms, final long time, final TimeUnit unit ) throws InterruptedException
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
