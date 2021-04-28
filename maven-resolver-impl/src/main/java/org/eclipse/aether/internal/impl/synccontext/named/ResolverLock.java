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

import org.eclipse.aether.named.NamedLock;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Resolver lock.
 */
public final class ResolverLock implements Closeable
{
    private final String key;

    private final NamedLock namedLock;

    private final boolean requestedShared;

    private final boolean effectiveShared;

    public ResolverLock( final String key,
                         final NamedLock namedLock,
                         final boolean requestedShared,
                         final boolean effectiveShared )
    {
        this.key = Objects.requireNonNull( key );
        this.namedLock = Objects.requireNonNull( namedLock );
        this.requestedShared = requestedShared;
        this.effectiveShared = effectiveShared;
    }

    public String key()
    {
        return key;
    }

    public boolean isRequestedShared()
    {
        return requestedShared;
    }

    public boolean isEffectiveShared()
    {
        return effectiveShared;
    }

    public boolean tryLock( final long time, final TimeUnit unit ) throws InterruptedException
    {
        if ( effectiveShared )
        {
            return namedLock.lockShared( time, unit );
        }
        else
        {
            return namedLock.lockExclusively( time, unit );
        }
    }

    public void unlock()
    {
        namedLock.unlock();
    }

    @Override
    public void close()
    {
        namedLock.close();
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
        ResolverLock that = (ResolverLock) o;
        return key.equals( that.key );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( key );
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName()
               + "{"
               + "key='" + key + '\''
               + ", namedLock=" + namedLock
               + ", requestedShared=" + requestedShared
               + ", effectiveShared=" + effectiveShared
               + '}';
    }
}
