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

import org.eclipse.aether.named.NamedLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Support class for {@link NamedLockFactory} implementations providing reference counting.
 *
 * @param <I> the backing implementation type.
 */
public abstract class NamedLockFactorySupport<I> implements NamedLockFactory
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    private final ConcurrentMap<String, NamedLockHolder<I>> locks;

    public NamedLockFactorySupport()
    {
        this.locks = new ConcurrentHashMap<>();
    }

    @Override
    public NamedLockSupport getLock( final String name )
    {
        return locks.compute( name, ( k, v ) ->
        {
            if ( v == null )
            {
                v = createLock( k );
            }
            v.incRef();
            return v;
        } ).namedLock;
    }

    @Override
    public void shutdown()
    {
        // override if needed
    }

    public void closeLock( final String name )
    {
        locks.compute( name, ( k, v ) ->
        {
            if ( v != null && v.decRef() == 0 )
            {
                destroyLock( k, v );
                return null;
            }
            return v;
        } );
    }


    @Override
    protected void finalize() throws Throwable
    {
        try
        {
            if ( !locks.isEmpty() )
            {
                // report leak
                logger.warn( "Lock leak, referenced locks still exist {}", locks );
            }
        }
        finally
        {
            super.finalize();
        }
    }

    /**
     * Implementation should create and return {@link NamedLockSupport} for given {@code name}, this method should never
     * return {@code null}.
     */
    protected abstract NamedLockHolder<I> createLock( final String name );

    /**
     * Implementation may override this (empty) method to perform some sort of implementation specific clean-up for
     * given name and holder. Invoked when reference count for holder returned by {@link #createLock(String)} drops to
     * zero.
     */
    protected void destroyLock( final String name, final NamedLockHolder<I> holder )
    {
        // override if needed
    }

    /**
     * This class is a "holder" for backing implementation (if needed), named lock and reference count.
     *
     * @param <I>
     */
    protected static final class NamedLockHolder<I>
    {
        private final I implementation;

        private final NamedLockSupport namedLock;

        private final AtomicInteger referenceCount;

        public NamedLockHolder( final I implementation, final NamedLockSupport namedLock )
        {
            this.implementation = implementation;
            this.namedLock = Objects.requireNonNull( namedLock );
            this.referenceCount = new AtomicInteger( 0 );
        }

        public I getImplementation()
        {
            return implementation;
        }

        private int incRef()
        {
            return referenceCount.incrementAndGet();
        }

        private int decRef()
        {
            return referenceCount.decrementAndGet();
        }

        @Override
        public String toString()
        {
            return "[refCount=" + referenceCount.get() + ", lock=" + namedLock + "]";
        }
    }
}
