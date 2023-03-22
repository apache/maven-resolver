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
package org.eclipse.aether.internal.impl.synccontext.named;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Adapter to adapt {@link NamedLockFactory} and {@link NamedLock} to {@link SyncContext}.
 */
public final class NamedLockFactoryAdapter {
    public static final String TIME_KEY = "aether.syncContext.named.time";

    public static final long DEFAULT_TIME = 30L;

    public static final String TIME_UNIT_KEY = "aether.syncContext.named.time.unit";

    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    private final NameMapper nameMapper;

    private final NamedLockFactory namedLockFactory;

    public NamedLockFactoryAdapter(final NameMapper nameMapper, final NamedLockFactory namedLockFactory) {
        this.nameMapper = requireNonNull(nameMapper);
        this.namedLockFactory = requireNonNull(namedLockFactory);
        // TODO: this is ad-hoc "validation", experimental and likely to change
        if (this.namedLockFactory instanceof FileLockNamedLockFactory && !this.nameMapper.isFileSystemFriendly()) {
            throw new IllegalArgumentException(
                    "Misconfiguration: FileLockNamedLockFactory lock factory requires FS friendly NameMapper");
        }
    }

    public SyncContext newInstance(final RepositorySystemSession session, final boolean shared) {
        return new AdaptedLockSyncContext(session, shared, nameMapper, namedLockFactory);
    }

    /**
     * @since 1.9.1
     */
    public NameMapper getNameMapper() {
        return nameMapper;
    }

    /**
     * @since 1.9.1
     */
    public NamedLockFactory getNamedLockFactory() {
        return namedLockFactory;
    }

    public String toString() {
        return getClass().getSimpleName()
                + "(nameMapper=" + nameMapper
                + ", namedLockFactory=" + namedLockFactory
                + ")";
    }

    private static class AdaptedLockSyncContext implements SyncContext {
        private static final Logger LOGGER = LoggerFactory.getLogger(AdaptedLockSyncContext.class);

        private final RepositorySystemSession session;

        private final boolean shared;

        private final NameMapper lockNaming;

        private final NamedLockFactory namedLockFactory;

        private final long time;

        private final TimeUnit timeUnit;

        private final Deque<NamedLock> locks;

        private AdaptedLockSyncContext(
                final RepositorySystemSession session,
                final boolean shared,
                final NameMapper lockNaming,
                final NamedLockFactory namedLockFactory) {
            this.session = session;
            this.shared = shared;
            this.lockNaming = lockNaming;
            this.namedLockFactory = namedLockFactory;
            this.time = getTime(session);
            this.timeUnit = getTimeUnit(session);
            this.locks = new ArrayDeque<>();

            if (time < 0L) {
                throw new IllegalArgumentException("time cannot be negative");
            }
        }

        private long getTime(final RepositorySystemSession session) {
            return ConfigUtils.getLong(session, DEFAULT_TIME, TIME_KEY);
        }

        private TimeUnit getTimeUnit(final RepositorySystemSession session) {
            return TimeUnit.valueOf(ConfigUtils.getString(session, DEFAULT_TIME_UNIT.name(), TIME_UNIT_KEY));
        }

        @Override
        public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
            Collection<String> keys = lockNaming.nameLocks(session, artifacts, metadatas);
            if (keys.isEmpty()) {
                return;
            }

            LOGGER.trace("Need {} {} lock(s) for {}", keys.size(), shared ? "read" : "write", keys);
            int acquiredLockCount = 0;
            for (String key : keys) {
                NamedLock namedLock = namedLockFactory.getLock(key);
                try {
                    LOGGER.trace("Acquiring {} lock for '{}'", shared ? "read" : "write", key);

                    boolean locked;
                    if (shared) {
                        locked = namedLock.lockShared(time, timeUnit);
                    } else {
                        locked = namedLock.lockExclusively(time, timeUnit);
                    }

                    if (!locked) {
                        LOGGER.trace("Failed to acquire {} lock for '{}'", shared ? "read" : "write", key);

                        namedLock.close();
                        throw new IllegalStateException("Could not acquire " + (shared ? "read" : "write")
                                + " lock for '" + namedLock.name() + "'");
                    }

                    locks.push(namedLock);
                    acquiredLockCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            LOGGER.trace("Total locks acquired: {}", acquiredLockCount);
        }

        @Override
        public void close() {
            if (locks.isEmpty()) {
                return;
            }

            // Release locks in reverse insertion order
            int released = 0;
            while (!locks.isEmpty()) {
                try (NamedLock namedLock = locks.pop()) {
                    LOGGER.trace("Releasing {} lock for '{}'", shared ? "read" : "write", namedLock.name());
                    namedLock.unlock();
                    released++;
                }
            }
            LOGGER.trace("Total locks released: {}", released);
        }
    }
}
