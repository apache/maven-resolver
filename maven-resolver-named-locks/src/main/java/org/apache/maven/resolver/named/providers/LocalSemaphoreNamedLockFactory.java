package org.apache.maven.resolver.named.providers;

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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.resolver.named.support.AdaptedSemaphoreNamedLock;
import org.apache.maven.resolver.named.support.NamedLockFactorySupport;

/**
 * A JVM-local named lock factory that uses named {@link Semaphore}s.
 */
@Singleton
@Named( LocalSemaphoreNamedLockFactory.NAME )
public class LocalSemaphoreNamedLockFactory
    extends NamedLockFactorySupport
{
    public static final String NAME = "semaphore-local";

    @Override
    protected AdaptedSemaphoreNamedLock createLock( final String name )
    {
      Semaphore semaphore = new Semaphore( Integer.MAX_VALUE );
      return new AdaptedSemaphoreNamedLock( name, this, new JVMSemaphore( semaphore ) );
    }

    /**
     * Adapted JVM {@link java.util.concurrent.Semaphore}.
     */
    private static final class JVMSemaphore
        implements AdaptedSemaphoreNamedLock.AdaptedSemaphore
    {
        private final Semaphore semaphore;

        private JVMSemaphore( final Semaphore semaphore )
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
