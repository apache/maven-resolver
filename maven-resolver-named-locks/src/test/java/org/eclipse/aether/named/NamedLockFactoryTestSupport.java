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
package org.eclipse.aether.named;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.support.LockUpgradeNotSupportedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT support for {@link NamedLockFactory}.
 */
public abstract class NamedLockFactoryTestSupport {

    protected static NamedLockFactory namedLockFactory;

    protected Collection<NamedLockKey> lockName(TestInfo testInfo) {
        return Collections.singleton(NamedLockKey.of(testInfo.getDisplayName()));
    }

    @Test
    void testFailure(TestInfo testInfo) throws InterruptedException {
        // note: set system property "aether.named.diagnostic.enabled" to "true" to have log output
        // this test does NOT assert its presence, only the proper flow
        assertThrows(IllegalStateException.class, () -> {
            Thread t1 = new Thread(() -> {
                try {
                    namedLockFactory.getLock(lockName(testInfo)).lockShared(1L, TimeUnit.MINUTES);
                    namedLockFactory.getLock(lockName(testInfo)).lockShared(1L, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    namedLockFactory.getLock(lockName(testInfo)).lockShared(1L, TimeUnit.MINUTES);
                    namedLockFactory.getLock(lockName(testInfo)).lockShared(1L, TimeUnit.MINUTES);
                    namedLockFactory.getLock(lockName(testInfo)).lockShared(1L, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            throw namedLockFactory.onFailure(new IllegalStateException("failure"));
        });
    }

    @Test
    void refCounting(TestInfo testInfo) {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys);
                NamedLock two = namedLockFactory.getLock(keys)) {
            assertSame(one, two);
            one.close();
            two.close();

            try (NamedLock three = namedLockFactory.getLock(keys)) {
                assertNotSame(three, two);
            }
        }
    }

    @Test
    void unlockWoLock(TestInfo testInfo) {
        assertThrows(IllegalStateException.class, () -> {
            final Collection<NamedLockKey> keys = lockName(testInfo);
            try (NamedLock one = namedLockFactory.getLock(keys)) {
                one.unlock();
            }
        });
    }

    @Test
    void wwBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rrBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void wrBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rwBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            try {
                one.lockExclusively(1L, TimeUnit.MILLISECONDS);
                fail();
            } catch (LockUpgradeNotSupportedException e) {
                // good
            }
            one.unlock();
        }
    }

    @Test
    @Timeout(5)
    public void sharedAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, true, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test
    @Timeout(5)
    public void exclusiveAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, false, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test
    @Timeout(5)
    public void mixedAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test
    @Timeout(5)
    public void fullyConsumeLockTime(TestInfo testInfo) throws InterruptedException {
        long start = System.nanoTime();
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
        long end = System.nanoTime();
        long duration = end - start;
        long expectedDuration = TimeUnit.MILLISECONDS.toNanos(ACCESS_WAIT_MILLIS);
        assertTrue(duration >= expectedDuration, duration + " >= " + expectedDuration); // equal in ideal case
    }

    @Test
    @Timeout(5)
    public void releasedExclusiveAllowAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, winners, losers));
        try (NamedLock namedLock = namedLockFactory.getLock(keys)) {
            assertTrue(namedLock.lockExclusively(50L, TimeUnit.MILLISECONDS));
            try {
                t1.start();
                Thread.sleep(50L);
            } finally {
                namedLock.unlock();
            }
        }
        t1.join();
        winners.await();
        losers.await();
    }

    private static final long ACCESS_WAIT_MILLIS = 1000L;

    private static class Access implements Runnable {
        final NamedLockFactory namedLockFactory;
        final Collection<NamedLockKey> keys;
        final boolean shared;
        final CountDownLatch winner;
        final CountDownLatch loser;

        public Access(
                NamedLockFactory namedLockFactory,
                Collection<NamedLockKey> keys,
                boolean shared,
                CountDownLatch winner,
                CountDownLatch loser) {
            this.namedLockFactory = namedLockFactory;
            this.keys = keys;
            this.shared = shared;
            this.winner = winner;
            this.loser = loser;
        }

        @Override
        public void run() {
            try (NamedLock lock = namedLockFactory.getLock(keys)) {
                if (shared
                        ? lock.lockShared(ACCESS_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                        : lock.lockExclusively(ACCESS_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    try {
                        winner.countDown();
                        loser.await();
                    } finally {
                        lock.unlock();
                    }
                } else {
                    loser.countDown();
                    winner.await();
                }
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
        }
    }
}
