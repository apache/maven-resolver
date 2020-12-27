package org.eclipse.aether.synccontext;

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

import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Priority;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A singleton factory to create synchronization contexts using a global lock based on
 * {@link ReentrantReadWriteLock}. Explicit artifacts and metadata passed are ignored.
 * <p>
 * <strong>Note: This component is still considered to be experimental, use with caution!</strong>
 */
@Named
@Priority( Integer.MAX_VALUE )
@Singleton
public class GlobalSyncContextFactory
    implements SyncContextFactory
{
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public SyncContext newInstance( RepositorySystemSession session, boolean shared )
    {
        return new GlobalSyncContext( shared ? lock.readLock() : lock.writeLock(), shared );
    }

    static class GlobalSyncContext
        implements SyncContext
    {
        private static final Logger LOGGER = LoggerFactory.getLogger( GlobalSyncContext.class );

        private final Lock lock;
        private final boolean shared;
        private int lockHoldCount;

        private GlobalSyncContext( Lock lock, boolean shared )
        {
            this.lock = lock;
            this.shared = shared;
        }

        public void acquire( Collection<? extends Artifact> artifact, Collection<? extends Metadata> metadata )
        {
            LOGGER.trace( "Acquiring global {} lock (currently held: {})",
                          shared ? "read" : "write", lockHoldCount );
            lock.lock();
            lockHoldCount++;
        }

        public void close()
        {
            while ( lockHoldCount > 0 )
            {
                LOGGER.trace( "Releasing global {} lock (currently held: {})",
                              shared ? "read" : "write", lockHoldCount );
                lock.unlock();
                lockHoldCount--;
            }
        }

    }

}
