package org.eclipse.aether.internal.impl.concurrency;

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

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.concurrency.ResolverExecutor;
import org.eclipse.aether.spi.concurrency.ResolverExecutorService;
import org.eclipse.aether.util.concurrency.WorkerThreadFactory;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ResolverExecutor}.
 * <p>
 * This implementation uses {@link RepositorySystemSession#getData()} to store created {@link ExecutorService}
 * instances. It creates instances that may be eventually garbage collected, so no explicit shutdown happens on
 * them. When {@code maxThreads} parameter is 1 (accepted values are greater than zero), this implementation assumes
 * caller wants "direct execution" (on caller thread) and creates {@link ResolverExecutor} instances accordingly.
 */
@Singleton
@Named
public final class DefaultResolverExecutorService implements ResolverExecutorService
{
    @Override
    public Key getKey( Class<?> service, String... discriminators )
    {
        requireNonNull( service );
        return new KeyImpl( DefaultResolverExecutorService.class.getName()
                + "-" + service.getSimpleName()
                + String.join( "-", discriminators ) );
    }

    @Override
    public ResolverExecutor getResolverExecutor( RepositorySystemSession session, Key key, int maxThreads )
    {
        requireNonNull( session );
        requireNonNull( key );
        if ( maxThreads < 1 )
        {
            throw new IllegalArgumentException( "maxThreads must be greater than zero" );
        }

        final ExecutorService executorService;
        if ( maxThreads == 1 ) // direct
        {
            executorService = null;
        }
        else // shared && pooled
        {
            executorService = (ExecutorService) session.getData()
                    .computeIfAbsent( key, () -> createExecutorService( key, maxThreads ) );
        }
        return new DefaultResolverExecutor( executorService );
    }

    /**
     * Creates am {@link ExecutorService} that allows its core threads to die off in case of inactivity, and allows
     * for proper garbage collection. This is important detail, as these instances are kept within session data, and
     * currently there is no way to shut down them.
     */
    private ExecutorService createExecutorService( Key key, int maxThreads )
    {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                3L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new WorkerThreadFactory( key.asString() + "-" )
        );
        threadPoolExecutor.allowCoreThreadTimeOut( true );
        return threadPoolExecutor;
    }

    private static class KeyImpl implements Key
    {
        private final String keyString;

        private KeyImpl( String keyString )
        {
            this.keyString = keyString;
        }

        @Override
        public String asString()
        {
            return keyString;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            KeyImpl key = (KeyImpl) o;
            return keyString.equals( key.keyString );
        }

        @Override
        public int hashCode()
        {
            return hash( keyString );
        }
    }
}