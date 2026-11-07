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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT support for {@link NamedLockFactory}. Subclass MUST populate the {@link #namedLockFactory} field in a
 * {@link org.junit.jupiter.api.BeforeAll} method; the factory is shut down once all tests of the class ran.
 * <p>
 * Published in the {@code test-jar} of this module for the named lock implementations living in other modules.
 */
public abstract class NamedLockFactoryTestSupport {

    protected static NamedLockFactory namedLockFactory;

    @AfterAll
    static void shutdownNamedLockFactory() {
        if (namedLockFactory != null) {
            namedLockFactory.shutdown();
            namedLockFactory = null;
        }
    }

    protected Collection<NamedLockKey> lockName(TestInfo testInfo) {
        return Collections.singleton(NamedLockKey.of(testInfo.getDisplayName()));
    }

    /**
     * How long the boxing tests wait for a lock that is expected to be free. Implementations that talk to another
     * process may need more than the default.
     */
    protected long lockWaitMillis() {
        return 1L;
    }

    /**
     * How long a contending {@link Access} waits for a lock; the loser of a contended lock waits this long before
     * giving up. {@link #fullyConsumeLockTime(TestInfo)} asserts that the loser really waited this long.
     */
    protected long accessWaitMillis() {
        return 1000L;
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
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertThrows(IllegalStateException.class, one::unlock);
        }
    }

    @Test
    void wwBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockExclusively(lockWaitMillis(), TimeUnit.MILLISECONDS));
            assertTrue(one.lockExclusively(lockWaitMillis(), TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rrBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockShared(lockWaitMillis(), TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(lockWaitMillis(), TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void wrBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockExclusively(lockWaitMillis(), TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(lockWaitMillis(), TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rwBoxing(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        try (NamedLock one = namedLockFactory.getLock(keys)) {
            assertTrue(one.lockShared(lockWaitMillis(), TimeUnit.MILLISECONDS));
            try {
                one.lockExclusively(lockWaitMillis(), TimeUnit.MILLISECONDS);
                fail("lock upgrade should be not supported");
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
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, accessWaitMillis(), winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, true, accessWaitMillis(), winners, losers));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);
        assertTrue(winners.await(5, TimeUnit.SECONDS), "expected both threads to win");
        assertTrue(losers.await(5, TimeUnit.SECONDS), "expected no loser");
    }

    @Test
    @Timeout(5)
    public void exclusiveAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, false, accessWaitMillis(), winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, accessWaitMillis(), winners, losers));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);
        assertTrue(winners.await(5, TimeUnit.SECONDS), "expected a winner");
        assertTrue(losers.await(5, TimeUnit.SECONDS), "expected a loser");
    }

    @Test
    @Timeout(5)
    public void mixedAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, accessWaitMillis(), winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, accessWaitMillis(), winners, losers));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);
        assertTrue(winners.await(5, TimeUnit.SECONDS), "expected a winner");
        assertTrue(losers.await(5, TimeUnit.SECONDS), "expected a loser");
    }

    @Test
    @Timeout(5)
    public void fullyConsumeLockTime(TestInfo testInfo) throws InterruptedException {
        long start = System.nanoTime();
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, accessWaitMillis(), winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, keys, false, accessWaitMillis(), winners, losers));
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);
        assertTrue(winners.await(5, TimeUnit.SECONDS), "expected a winner");
        assertTrue(losers.await(5, TimeUnit.SECONDS), "expected a loser");
        long end = System.nanoTime();
        long duration = end - start;
        long expectedDuration = TimeUnit.MILLISECONDS.toNanos(accessWaitMillis());
        assertTrue(duration >= expectedDuration, duration + " >= " + expectedDuration); // equal in ideal case
    }

    @Test
    @Timeout(5)
    public void releasedExclusiveAllowAccess(TestInfo testInfo) throws InterruptedException {
        final Collection<NamedLockKey> keys = lockName(testInfo);
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 loser
        Thread t1 = new Thread(new Access(namedLockFactory, keys, true, accessWaitMillis(), winners, losers));
        try (NamedLock namedLock = namedLockFactory.getLock(keys)) {
            assertTrue(namedLock.lockExclusively(50L, TimeUnit.MILLISECONDS));
            try {
                t1.start();
                Thread.sleep(50L);
            } finally {
                namedLock.unlock();
            }
        }
        t1.join(5000);
        assertTrue(winners.await(5, TimeUnit.SECONDS), "expected a winner");
        assertTrue(losers.await(5, TimeUnit.SECONDS), "expected no loser");
    }

    private static class Access implements Runnable {
        final NamedLockFactory namedLockFactory;
        final Collection<NamedLockKey> keys;
        final boolean shared;
        final long waitMillis;
        final CountDownLatch winner;
        final CountDownLatch loser;

        Access(
                NamedLockFactory namedLockFactory,
                Collection<NamedLockKey> keys,
                boolean shared,
                long waitMillis,
                CountDownLatch winner,
                CountDownLatch loser) {
            this.namedLockFactory = namedLockFactory;
            this.keys = keys;
            this.shared = shared;
            this.waitMillis = waitMillis;
            this.winner = winner;
            this.loser = loser;
        }

        @Override
        public void run() {
            try (NamedLock lock = namedLockFactory.getLock(keys)) {
                if (shared
                        ? lock.lockShared(waitMillis, TimeUnit.MILLISECONDS)
                        : lock.lockExclusively(waitMillis, TimeUnit.MILLISECONDS)) {
                    try {
                        winner.countDown();
                        loser.await(5, TimeUnit.SECONDS);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    loser.countDown();
                    winner.await(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
        }
    }
}
