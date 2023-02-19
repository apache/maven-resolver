/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.named.support;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.named.NamedLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for {@link NamedLockFactory} implementations providing reference counting.
 */
public abstract class NamedLockFactorySupport implements NamedLockFactory {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<String, NamedLockHolder> locks;

    public NamedLockFactorySupport() {
        this.locks = new ConcurrentHashMap<>();
    }

    @Override
    public NamedLockSupport getLock(final String name) {
        return locks.compute(name, (k, v) -> {
                    if (v == null) {
                        v = new NamedLockHolder(createLock(k));
                    }
                    v.incRef();
                    return v;
                })
                .namedLock;
    }

    @Override
    public void shutdown() {
        // override if needed
    }

    public void closeLock(final String name) {
        locks.compute(name, (k, v) -> {
            if (v != null && v.decRef() == 0) {
                destroyLock(v.namedLock.name());
                return null;
            }
            return v;
        });
    }

    /**
     * Implementations shall create and return {@link NamedLockSupport} for given {@code name}, this method must never
     * return {@code null}.
     */
    protected abstract NamedLockSupport createLock(String name);

    /**
     * Implementation may override this (empty) method to perform some sort of implementation specific cleanup for
     * given lock name. Invoked when reference count for given name drops to zero and named lock was removed.
     */
    protected void destroyLock(final String name) {
        // override if needed
    }

    private static final class NamedLockHolder {
        private final NamedLockSupport namedLock;

        private final AtomicInteger referenceCount;

        private NamedLockHolder(final NamedLockSupport namedLock) {
            this.namedLock = Objects.requireNonNull(namedLock);
            this.referenceCount = new AtomicInteger(0);
        }

        private int incRef() {
            return referenceCount.incrementAndGet();
        }

        private int decRef() {
            return referenceCount.decrementAndGet();
        }

        @Override
        public String toString() {
            return "[refCount=" + referenceCount.get() + ", lock=" + namedLock + "]";
        }
    }
}
