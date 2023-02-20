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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.eclipse.aether.named.support.Retry.retry;

/**
 * Named lock that uses {@link FileLock}. An instance of this class is about ONE LOCK (one file)
 * and is possibly used by multiple threads. Each thread (if properly coded re boxing) will try to
 * obtain either shared or exclusive lock. As file locks are JVM-scoped (so one JVM can obtain
 * same file lock only once), the threads share file lock and synchronize according to it. Still,
 * as file lock obtain operation does not block (or in other words, the method that does block
 * cannot be controlled for how long it blocks), we are "simulating" thread blocking using
 * {@link Retry} utility.
 * This implementation performs coordination not only on thread (JVM-local) level, but also on
 * process level, as long as other parties are using this same "advisory" locking mechanism.
 *
 * @since 1.7.3
 */
public final class FileLockNamedLock extends NamedLockSupport {
    private static final long RETRY_SLEEP_MILLIS = 100L;

    private static final long LOCK_POSITION = 0L;

    private static final long LOCK_SIZE = 1L;

    /**
     * Thread -> steps stack (true = shared, false = exclusive)
     */
    private final Map<Thread, Deque<Boolean>> threadSteps;

    /**
     * The {@link FileChannel} this instance is about.
     */
    private final FileChannel fileChannel;

    /**
     * The reference of {@link FileLock}, if obtained.
     */
    private final AtomicReference<FileLock> fileLockRef;

    /**
     * Lock protecting "critical region": this is where threads are allowed to perform locking but should leave this
     * region as quick as possible.
     */
    private final ReentrantLock criticalRegion;

    public FileLockNamedLock(final String name, final FileChannel fileChannel, final NamedLockFactorySupport factory) {
        super(name, factory);
        this.threadSteps = new HashMap<>();
        this.fileChannel = fileChannel;
        this.fileLockRef = new AtomicReference<>(null);
        this.criticalRegion = new ReentrantLock();
    }

    @Override
    public boolean lockShared(final long time, final TimeUnit unit) throws InterruptedException {
        return retry(time, unit, RETRY_SLEEP_MILLIS, this::doLockShared, null, false);
    }

    @Override
    public boolean lockExclusively(final long time, final TimeUnit unit) throws InterruptedException {
        return retry(time, unit, RETRY_SLEEP_MILLIS, this::doLockExclusively, null, false);
    }

    private Boolean doLockShared() {
        if (criticalRegion.tryLock()) {
            try {
                Deque<Boolean> steps = threadSteps.computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>());
                FileLock obtainedLock = fileLockRef.get();
                if (obtainedLock != null) {
                    if (obtainedLock.isShared()) {
                        steps.push(Boolean.TRUE);
                        return true;
                    } else {
                        // if we own exclusive, that's still fine
                        boolean weOwnExclusive = steps.contains(Boolean.FALSE);
                        if (weOwnExclusive) {
                            steps.push(Boolean.TRUE);
                            return true;
                        } else {
                            // someone else owns exclusive, let's wait
                            return null;
                        }
                    }
                }

                FileLock fileLock = obtainFileLock(true);
                if (fileLock != null) {
                    fileLockRef.set(fileLock);
                    steps.push(Boolean.TRUE);
                    return true;
                }
            } finally {
                criticalRegion.unlock();
            }
        }
        return null;
    }

    private Boolean doLockExclusively() {
        if (criticalRegion.tryLock()) {
            try {
                Deque<Boolean> steps = threadSteps.computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>());
                FileLock obtainedLock = fileLockRef.get();
                if (obtainedLock != null) {
                    if (obtainedLock.isShared()) {
                        // if we own shared, that's attempted upgrade
                        boolean weOwnShared = steps.contains(Boolean.TRUE);
                        if (weOwnShared) {
                            return false; // Lock upgrade not supported
                        } else {
                            // someone else owns shared, let's wait
                            return null;
                        }
                    } else {
                        // if we own exclusive, that's fine
                        boolean weOwnExclusive = steps.contains(Boolean.FALSE);
                        if (weOwnExclusive) {
                            steps.push(Boolean.FALSE);
                            return true;
                        } else {
                            // someone else owns exclusive, let's wait
                            return null;
                        }
                    }
                }

                FileLock fileLock = obtainFileLock(false);
                if (fileLock != null) {
                    fileLockRef.set(fileLock);
                    steps.push(Boolean.FALSE);
                    return true;
                }
            } finally {
                criticalRegion.unlock();
            }
        }
        return null;
    }

    @Override
    public void unlock() {
        criticalRegion.lock();
        try {
            Deque<Boolean> steps = threadSteps.computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>());
            if (steps.isEmpty()) {
                throw new IllegalStateException("Wrong API usage: unlock without lock");
            }
            steps.pop();
            if (steps.isEmpty() && !anyOtherThreadHasSteps()) {
                try {
                    fileLockRef.getAndSet(null).release();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } finally {
            criticalRegion.unlock();
        }
    }

    /**
     * Returns {@code true} if any other than this thread using this instance has any step recorded.
     */
    private boolean anyOtherThreadHasSteps() {
        return threadSteps.entrySet().stream()
                .filter(e -> !Thread.currentThread().equals(e.getKey()))
                .map(Map.Entry::getValue)
                .anyMatch(d -> !d.isEmpty());
    }

    /**
     * Attempts to obtain real {@link FileLock}, returns non-null value is succeeds, or {@code null} if cannot.
     */
    private FileLock obtainFileLock(final boolean shared) {
        FileLock result;
        try {
            result = fileChannel.tryLock(LOCK_POSITION, LOCK_SIZE, shared);
        } catch (OverlappingFileLockException e) {
            logger.trace("File lock overlap on '{}'", name(), e);
            return null;
        } catch (IOException e) {
            logger.trace("Failure on acquire of file lock for '{}'", name(), e);
            throw new UncheckedIOException("Failed to acquire lock file channel for '" + name() + "'", e);
        }
        return result;
    }
}
