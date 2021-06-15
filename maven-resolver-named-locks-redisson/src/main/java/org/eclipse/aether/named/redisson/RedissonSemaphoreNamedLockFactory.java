package org.eclipse.aether.named.redisson;

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

import org.eclipse.aether.named.support.AdaptedSemaphoreNamedLock;
import org.eclipse.aether.named.support.NamedLockSupport;
import org.redisson.api.RSemaphore;

import javax.inject.Named;
import javax.inject.Singleton;
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

    @Override
    protected NamedLockSupport createLock( final String name )
    {
        return new AdaptedSemaphoreNamedLock(
                   name, this, new RedissonSemaphore( redissonClient.getSemaphore( NAME_PREFIX + name ) )
    );
    }

    private static final class RedissonSemaphore implements AdaptedSemaphoreNamedLock.AdaptedSemaphore
    {
        private final RSemaphore semaphore;

        private RedissonSemaphore( final RSemaphore semaphore )
        {
            semaphore.trySetPermits( Integer.MAX_VALUE );
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
