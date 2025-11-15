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
import java.util.ArrayList;
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

    public static final long DEFAULT_TIME = 900L;

    public static final String TIME_UNIT_KEY = "aether.syncContext.named.time.unit";

    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    public static final String RETRY_KEY = "aether.syncContext.named.retry";

    public static final int DEFAULT_RETRY = 1;

    public static final String RETRY_WAIT_KEY = "aether.syncContext.named.retry.wait";

    public static final long DEFAULT_RETRY_WAIT = 200L;

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

        private final int retry;

        private final long retryWait;

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
            this.retry = getRetry(session);
            this.retryWait = getRetryWait(session);
            this.locks = new ArrayDeque<>();

            if (time < 0L) {
                throw new IllegalArgumentException(TIME_KEY + " value cannot be negative");
            }
            if (retry < 0L) {
                throw new IllegalArgumentException(RETRY_KEY + " value cannot be negative");
            }
            if (retryWait < 0L) {
                throw new IllegalArgumentException(RETRY_WAIT_KEY + " value cannot be negative");
            }
        }

        private long getTime(final RepositorySystemSession session) {
            return ConfigUtils.getLong(session, DEFAULT_TIME, TIME_KEY);
        }

        private TimeUnit getTimeUnit(final RepositorySystemSession session) {
            return TimeUnit.valueOf(ConfigUtils.getString(session, DEFAULT_TIME_UNIT.name(), TIME_UNIT_KEY));
        }

        private int getRetry(final RepositorySystemSession session) {
            return ConfigUtils.getInteger(session, DEFAULT_RETRY, RETRY_KEY);
        }

        private long getRetryWait(final RepositorySystemSession session) {
            return ConfigUtils.getLong(session, DEFAULT_RETRY_WAIT, RETRY_WAIT_KEY);
        }

        @Override
        public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
            Collection<String> keys = lockNaming.nameLocks(session, artifacts, metadatas);
            if (keys.isEmpty()) {
                return;
            }

            final int attempts = retry + 1;
            final ArrayList<IllegalStateException> illegalStateExceptions = new ArrayList<>();
            for (int attempt = 1; attempt <= attempts; attempt++) {
                LOGGER.trace(
                        "Attempt {}: Need {} {} lock(s) for {}", attempt, keys.size(), shared ? "read" : "write", keys);
                int acquiredLockCount = 0;
                try {
                    if (attempt > 1) {
                        Thread.sleep(retryWait);
                    }
                    for (String key : keys) {
                        NamedLock namedLock = namedLockFactory.getLock(key);
                        LOGGER.trace("Acquiring {} lock for '{}'", shared ? "read" : "write", key);

                        boolean locked;
                        if (shared) {
                            locked = namedLock.lockShared(time, timeUnit);
                        } else {
                            locked = namedLock.lockExclusively(time, timeUnit);
                        }

                        if (!locked) {
                            String timeStr = time + " " + timeUnit;
                            LOGGER.trace(
                                    "Failed to acquire {} lock for '{}' in {}",
                                    shared ? "read" : "write",
                                    key,
                                    timeStr);

                            namedLock.close();
                            closeAll();
                            illegalStateExceptions.add(new IllegalStateException(
                                    "Attempt " + attempt + ": Could not acquire " + (shared ? "read" : "write")
                                            + " lock for '" + namedLock.name() + "' in " + timeStr));
                            break;
                        } else {
                            locks.push(namedLock);
                            acquiredLockCount++;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                LOGGER.trace("Attempt {}: Total locks acquired: {}", attempt, acquiredLockCount);
                if (acquiredLockCount == keys.size()) {
                    break;
                }
            }
            if (!illegalStateExceptions.isEmpty()) {
                FailedToAcquireLockException ex = new FailedToAcquireLockException(shared, "Could not acquire lock(s)");
                illegalStateExceptions.forEach(ex::addSuppressed);
                throw namedLockFactory.onFailure(ex);
            }
        }

        private void closeAll() {
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

        @Override
        public void close() {
            closeAll();
        }
    }
}
