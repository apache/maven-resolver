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

import org.eclipse.aether.spi.concurrency.ResolverExecutor;
import org.eclipse.aether.spi.concurrency.ResolverExecutorService;
import org.eclipse.aether.util.concurrency.WorkerThreadFactory;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ResolverExecutor}.
 */
@Singleton
@Named
public final class DefaultResolverExecutorService implements ResolverExecutorService
{
    @Override
    public Name getName( Class<?> service, String... discriminators )
    {
        requireNonNull( service );
        return new NameImpl( DefaultResolverExecutorService.class.getName()
                + "-" + service.getSimpleName()
                + String.join( "-", discriminators ) );
    }

    @Override
    public ResolverExecutor getResolverExecutor( Name name, int maxThreads )
    {
        requireNonNull( name );
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
            executorService = createExecutorService( name, maxThreads );
        }
        return new DefaultResolverExecutor( executorService );
    }

    /**
     * Creates am {@link ExecutorService} that allows its core threads to die off in case of inactivity, and allows
     * for proper garbage collection. This is important detail, as these instances are kept within session data, and
     * currently there is no way to shut down them.
     */
    private ExecutorService createExecutorService( Name name, int maxThreads )
    {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                3L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new WorkerThreadFactory( name.asString() + "-" )
        );
        threadPoolExecutor.allowCoreThreadTimeOut( true );
        return threadPoolExecutor;
    }

    private static class NameImpl implements Name
    {
        private final String nameString;

        private NameImpl( String nameString )
        {
            this.nameString = nameString;
        }

        @Override
        public String asString()
        {
            return nameString;
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
            NameImpl other = (NameImpl) o;
            return nameString.equals( other.nameString );
        }

        @Override
        public int hashCode()
        {
            return hash( nameString );
        }
    }
}