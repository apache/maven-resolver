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

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.NamedLockKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Support class for {@link NamedLockFactory} implementations providing reference counting.
 */
public abstract class NamedLockFactorySupport implements NamedLockFactory {
    /**
     * System property key to enable locking diagnostic collection.
     *
     * @since 1.9.11
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue false
     */
    public static final String SYSTEM_PROP_DIAGNOSTIC_ENABLED = "aether.named.diagnostic.enabled";

    private static final boolean DIAGNOSTIC_ENABLED = Boolean.getBoolean(SYSTEM_PROP_DIAGNOSTIC_ENABLED);

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<NamedLockKey, NamedLockHolder> locks;

    private final AtomicInteger compositeCounter;

    private final boolean diagnosticEnabled;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public NamedLockFactorySupport() {
        this(DIAGNOSTIC_ENABLED);
    }

    public NamedLockFactorySupport(boolean diagnosticEnabled) {
        this.locks = new ConcurrentHashMap<>();
        this.compositeCounter = new AtomicInteger(0);
        this.diagnosticEnabled = diagnosticEnabled;
    }

    /**
     * Returns {@code true} if factory diagnostic collection is enabled.
     *
     * @since 1.9.11
     */
    public boolean isDiagnosticEnabled() {
        return diagnosticEnabled;
    }

    @Override
    public final NamedLock getLock(final Collection<NamedLockKey> keys) {
        requireNonNull(keys, "keys");
        if (shutdown.get()) {
            throw new IllegalStateException("factory already shut down");
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("empty keys");
        } else {
            return doGetLock(keys);
        }
    }

    protected NamedLock doGetLock(final Collection<NamedLockKey> keys) {
        if (keys.size() == 1) {
            NamedLockKey key = keys.iterator().next();
            return getLockAndRefTrack(key, () -> createLock(key));
        } else {
            return new CompositeNamedLock(
                    NamedLockKey.of(
                            "composite-" + compositeCounter.incrementAndGet(),
                            keys.stream()
                                    .map(NamedLockKey::resources)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toList())),
                    this,
                    keys.stream()
                            .map(k -> getLockAndRefTrack(k, () -> createLock(k)))
                            .collect(Collectors.toList()));
        }
    }

    protected NamedLock getLockAndRefTrack(final NamedLockKey key, Supplier<NamedLockSupport> supplier) {
        if (shutdown.get()) {
            throw new IllegalStateException("factory already shut down");
        }
        // Fast path: lock-free volatile read + atomic CAS increment.
        // ConcurrentHashMap.get() is a volatile read — no bucket locking.
        // In the common case (lock already exists), this avoids compute()'s
        // per-bucket exclusive lock entirely.
        NamedLockHolder holder = locks.get(key);
        if (holder != null && holder.tryIncRef()) {
            return holder.namedLock;
        }
        // Slow path: holder absent or being closed (refcount hit 0).
        // Use compute() to atomically create a new holder.
        return locks.compute(key, (k, v) -> {
                    if (shutdown.get()) {
                        throw new IllegalStateException("factory already shut down");
                    }
                    if (v == null || !v.tryIncRef()) {
                        v = new NamedLockHolder(supplier.get());
                        v.incRef();
                    }
                    return v;
                })
                .namedLock;
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            doShutdown();
        }
    }

    protected void doShutdown() {
        // override if needed
    }

    @Override
    public <E extends Throwable> E onFailure(E failure) {
        if (isDiagnosticEnabled()) {
            Map<NamedLockKey, NamedLockHolder> locks = new HashMap<>(this.locks); // copy
            int activeLocks = locks.size();
            logger.info("Diagnostic dump of lock factory");
            logger.info("===============================");
            logger.info("Implementation: {}", getClass().getName());
            logger.info("Active locks: {}", activeLocks);
            logger.info("");
            if (activeLocks > 0) {
                for (Map.Entry<NamedLockKey, NamedLockHolder> entry : locks.entrySet()) {
                    NamedLockKey key = entry.getKey();
                    int refCount = entry.getValue().referenceCount.get();
                    NamedLockSupport lock = entry.getValue().namedLock;
                    logger.info("Name: {}", key.name());
                    logger.info("RefCount: {}", refCount);
                    logger.info("Resources:");
                    key.resources().forEach(r -> logger.info(" - {}", r));
                    Map<Thread, Deque<String>> diag = lock.diagnosticState();
                    logger.info("State:");
                    diag.forEach((k, v) -> logger.info("  {} -> {}", k, v));
                }
                logger.info("");
            }
        }
        return failure;
    }

    public void closeLock(final NamedLockKey key) {
        locks.compute(key, (k, v) -> {
            if (v != null && v.decRef() == 0) {
                // Mark as closed to prevent a concurrent tryIncRef (lock-free fast path)
                // from reviving this holder. CAS ensures atomicity: if tryIncRef already
                // incremented from 0→1, our CAS fails and we keep the holder alive.
                if (v.referenceCount.compareAndSet(0, Integer.MIN_VALUE)) {
                    destroyLock(v.namedLock);
                    return null;
                }
                // A concurrent tryIncRef succeeded — holder is still in use, keep it
            }
            return v;
        });
    }

    /**
     * Implementations shall create and return {@link NamedLockSupport} for given {@code name}, this method must never
     * return {@code null}.
     */
    protected abstract NamedLockSupport createLock(NamedLockKey key);

    /**
     * Implementation may override this (empty) method to perform some sort of implementation specific cleanup for
     * given lock name. Invoked when reference count for given name drops to zero and named lock was removed.
     */
    protected void destroyLock(final NamedLock namedLock) {
        // override if needed
    }

    private static final class NamedLockHolder {
        private final NamedLockSupport namedLock;

        private final AtomicInteger referenceCount;

        private NamedLockHolder(final NamedLockSupport namedLock) {
            this.namedLock = requireNonNull(namedLock);
            this.referenceCount = new AtomicInteger(0);
        }

        private NamedLockHolder incRef() {
            referenceCount.incrementAndGet();
            return this;
        }

        /**
         * Atomically tries to increment the reference count. Returns {@code false} if the
         * holder has been closed (refcount &le; 0), preventing revival of a destroyed lock.
         * Used by the lock-free fast path in {@link #getLockAndRefTrack}.
         */
        private boolean tryIncRef() {
            while (true) {
                int current = referenceCount.get();
                if (current <= 0) {
                    return false;
                }
                if (referenceCount.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
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
