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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Adapter to adapt {@link NamedLockFactory} and {@link NamedLock} to {@link SyncContext}.
 */
public final class NamedLockFactoryAdapter {
    public static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_SYNC_CONTEXT + "named.";

    /**
     * The maximum of time amount to be blocked to obtain lock.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_TIME}
     */
    public static final String CONFIG_PROP_TIME = CONFIG_PROPS_PREFIX + "time";

    public static final long DEFAULT_TIME = 300L;

    /**
     * The maximum of time amount to be blocked to obtain exclusive lock (keep it low).
     *
     * @since 2.0.14
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_TIME_EXCLUSIVE}
     */
    public static final String CONFIG_PROP_TIME_EXCLUSIVE = CONFIG_PROPS_PREFIX + "exclusiveTime";

    public static final long DEFAULT_TIME_EXCLUSIVE = 5L;

    /**
     * The unit of maximum time amount to be blocked to obtain lock. Use TimeUnit enum names.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_TIME_UNIT}
     */
    public static final String CONFIG_PROP_TIME_UNIT = CONFIG_PROPS_PREFIX + "time.unit";

    public static final String DEFAULT_TIME_UNIT = "SECONDS";

    /**
     * The amount of retries on time-out.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_RETRY}
     */
    public static final String CONFIG_PROP_RETRY = CONFIG_PROPS_PREFIX + "retry";

    public static final int DEFAULT_RETRY = 1;

    /**
     * The amount of milliseconds to wait between retries on time-out.
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_RETRY_WAIT}
     */
    public static final String CONFIG_PROP_RETRY_WAIT = CONFIG_PROPS_PREFIX + "retry.wait";

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

        private final long sharedTime;

        private final long exclusiveTime;

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
            this.sharedTime = getTime(session, DEFAULT_TIME, CONFIG_PROP_TIME);
            this.exclusiveTime = getTime(session, DEFAULT_TIME_EXCLUSIVE, CONFIG_PROP_TIME_EXCLUSIVE, CONFIG_PROP_TIME);
            this.timeUnit = getTimeUnit(session);
            this.retry = getRetry(session);
            this.retryWait = getRetryWait(session);
            this.locks = new ArrayDeque<>();

            if (sharedTime < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_TIME + " value cannot be negative");
            }
            if (exclusiveTime < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_TIME_EXCLUSIVE + " value cannot be negative");
            }
            if (retry < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_RETRY + " value cannot be negative");
            }
            if (retryWait < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_RETRY_WAIT + " value cannot be negative");
            }
        }

        private long getTime(final RepositorySystemSession session, long defaultValue, String... keys) {
            return ConfigUtils.getLong(session, defaultValue, keys);
        }

        private TimeUnit getTimeUnit(final RepositorySystemSession session) {
            return TimeUnit.valueOf(ConfigUtils.getString(session, DEFAULT_TIME_UNIT, CONFIG_PROP_TIME_UNIT));
        }

        private int getRetry(final RepositorySystemSession session) {
            return ConfigUtils.getInteger(session, DEFAULT_RETRY, CONFIG_PROP_RETRY);
        }

        private long getRetryWait(final RepositorySystemSession session) {
            return ConfigUtils.getLong(session, DEFAULT_RETRY_WAIT, CONFIG_PROP_RETRY_WAIT);
        }

        @Override
        public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
            Collection<NamedLockKey> keys = lockNaming.nameLocks(session, artifacts, metadatas);
            if (keys.isEmpty()) {
                return;
            }

            final String timeStr = (shared ? sharedTime : exclusiveTime) + " " + timeUnit;
            final String lockKind = shared ? "shared" : "exclusive";
            final NamedLock namedLock = namedLockFactory.getLock(keys);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "Need {} lock for {} from {}",
                        lockKind,
                        namedLock.key().resources(),
                        namedLock.key().name());
            }

            final int attempts = retry + 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(
                            "Attempt {}: Acquire {} lock from {}",
                            attempt,
                            lockKind,
                            namedLock.key().name());
                }
                try {
                    if (attempt > 1) {
                        Thread.sleep(retryWait);
                    }
                    boolean locked;
                    if (shared) {
                        locked = namedLock.lockShared(sharedTime, timeUnit);
                    } else {
                        locked = namedLock.lockExclusively(exclusiveTime, timeUnit);
                    }

                    if (locked) {
                        // we are done, get out
                        locks.push(namedLock);
                        return;
                    }

                    // we failed; retry
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace(
                                "Failed to acquire {} lock for '{}' in {}",
                                lockKind,
                                namedLock.key().name(),
                                timeStr);
                    }
                } catch (InterruptedException e) {
                    // if we are here, means we were interrupted: fail
                    close();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            // if we are here, means all attempts were unsuccessful: fail
            close();
            FailedToAcquireLockException ex = new FailedToAcquireLockException(
                    shared,
                    "Could not acquire " + lockKind + " lock for "
                            + namedLock.key().resources() + " using lock "
                            + namedLock.key().name() + " in " + timeStr);
            throw namedLockFactory.onFailure(ex);
        }

        @Override
        public void close() {
            while (!locks.isEmpty()) {
                try (NamedLock namedLock = locks.pop()) {
                    namedLock.unlock();
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace(
                                "Unlocked and closed {} lock of {}", shared ? "shared" : "exclusive", namedLock.key());
                    }
                }
            }
        }
    }
}
