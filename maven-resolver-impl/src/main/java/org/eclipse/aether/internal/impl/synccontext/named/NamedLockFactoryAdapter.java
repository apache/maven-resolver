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
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.named.DefaultNamedLockFactorySelector;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
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
     * <strong>Deprecated: use {@code aether.system.named...} configuration instead.</strong>
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @deprecated
     */
    @Deprecated
    public static final String CONFIG_PROP_TIME = CONFIG_PROPS_PREFIX + "time";

    @Deprecated
    public static final long DEFAULT_TIME = DefaultNamedLockFactorySelector.DEFAULT_LOCK_WAIT_TIME;

    /**
     * The unit of maximum time amount to be blocked to obtain lock. Use TimeUnit enum names.
     * <strong>Deprecated: use {@code aether.system.named...} configuration instead.</strong>
     *
     * @since 1.7.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @deprecated
     */
    @Deprecated
    public static final String CONFIG_PROP_TIME_UNIT = CONFIG_PROPS_PREFIX + "time.unit";

    @Deprecated
    public static final String DEFAULT_TIME_UNIT = DefaultNamedLockFactorySelector.DEFAULT_LOCK_WAIT_TIME_UNIT;

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

    private final long lockWait;

    private final TimeUnit lockWaitUnit;

    public NamedLockFactoryAdapter(
            final NameMapper nameMapper,
            final NamedLockFactory namedLockFactory,
            long lockWait,
            TimeUnit lockWaitUnit) {
        this.nameMapper = requireNonNull(nameMapper);
        this.namedLockFactory = requireNonNull(namedLockFactory);
        this.lockWait = lockWait;
        this.lockWaitUnit = requireNonNull(lockWaitUnit);
        // TODO: this is ad-hoc "validation", experimental and likely to change
        if (this.namedLockFactory instanceof FileLockNamedLockFactory && !this.nameMapper.isFileSystemFriendly()) {
            throw new IllegalArgumentException(
                    "Misconfiguration: FileLockNamedLockFactory lock factory requires FS friendly NameMapper");
        }
    }

    public SyncContext newInstance(final RepositorySystemSession session, final boolean shared) {
        return new AdaptedLockSyncContext(session, shared, nameMapper, namedLockFactory, lockWait, lockWaitUnit);
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
                final NamedLockFactory namedLockFactory,
                final long lockWait,
                final TimeUnit lockWaitUnit) {
            this.session = session;
            this.shared = shared;
            this.lockNaming = lockNaming;
            this.namedLockFactory = namedLockFactory;
            this.time = lockWait;
            this.timeUnit = lockWaitUnit;
            this.retry = getRetry(session);
            this.retryWait = getRetryWait(session);
            this.locks = new ArrayDeque<>();

            if (retry < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_RETRY + " value cannot be negative");
            }
            if (retryWait < 0L) {
                throw new IllegalArgumentException(CONFIG_PROP_RETRY_WAIT + " value cannot be negative");
            }
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

            final String timeStr = time + " " + timeUnit;
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
                        locked = namedLock.lockShared(time, timeUnit);
                    } else {
                        locked = namedLock.lockExclusively(time, timeUnit);
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
            String message = "Could not acquire " + lockKind + " lock for "
                    + lockSubjects(artifacts, metadatas) + " in " + timeStr
                    + "; consider using '" + CONFIG_PROP_TIME
                    + "' property to increase lock timeout to a value that fits your environment";
            FailedToAcquireLockException ex = new FailedToAcquireLockException(shared, message);
            throw namedLockFactory.onFailure(ex);
        }

        private String lockSubjects(
                Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
            StringBuilder builder = new StringBuilder();
            if (artifacts != null && !artifacts.isEmpty()) {
                builder.append("artifacts: ")
                        .append(artifacts.stream().map(ArtifactIdUtils::toId).collect(Collectors.joining(", ")));
            }
            if (metadatas != null && !metadatas.isEmpty()) {
                if (builder.length() != 0) {
                    builder.append("; ");
                }
                builder.append("metadata: ")
                        .append(metadatas.stream().map(this::metadataSubjects).collect(Collectors.joining(", ")));
            }
            return builder.toString();
        }

        private String metadataSubjects(Metadata metadata) {
            String name = "";
            if (!metadata.getGroupId().isEmpty()) {
                name += metadata.getGroupId();
                if (!metadata.getArtifactId().isEmpty()) {
                    name += ":" + metadata.getArtifactId();
                    if (!metadata.getVersion().isEmpty()) {
                        name += ":" + metadata.getVersion();
                    }
                }
            }
            if (!metadata.getType().isEmpty()) {
                name += (name.isEmpty() ? "" : ":") + metadata.getType();
            }
            return name;
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
