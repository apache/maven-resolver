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
package org.eclipse.aether.named.hazelcast;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.support.LockUpgradeNotSupportedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UT support for {@link NamedLockFactory}.
 */
public abstract class NamedLockFactoryTestSupport {
    protected static final HazelcastClientUtils utils = new HazelcastClientUtils();

    protected static NamedLockFactory namedLockFactory;

    @AfterAll
    public static void cleanup() {
        if (namedLockFactory != null) {
            namedLockFactory.shutdown();
        }
        utils.cleanup();
    }

    @Test
    void refCounting(TestInfo testInfo) {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name);
                NamedLock two = namedLockFactory.getLock(name)) {
            assertSame(one, two);
            one.close();
            two.close();

            try (NamedLock three = namedLockFactory.getLock(name)) {
                assertNotSame(three, two);
            }
        }
    }

    @Test
    void unlockWoLock(TestInfo testInfo) {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThrows(IllegalStateException.class, one::unlock);
        }
    }

    @Test
    void wwBoxing(TestInfo testInfo) throws InterruptedException {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rrBoxing(TestInfo testInfo) throws InterruptedException {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void wrBoxing(TestInfo testInfo) throws InterruptedException {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    void rwBoxing(TestInfo testInfo) throws InterruptedException {
        final String name = testInfo.getDisplayName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            try {
                one.lockExclusively(1L, TimeUnit.MILLISECONDS);
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
        final String name = testInfo.getDisplayName();
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winner
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 loser
        Thread t1 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
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
        final String name = testInfo.getDisplayName();
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, name, false, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, name, false, winners, losers));
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
        final String name = testInfo.getDisplayName();
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, name, false, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    private static class Access implements Runnable {
        final NamedLockFactory namedLockFactory;
        final String name;
        final boolean shared;
        final CountDownLatch winner;
        final CountDownLatch loser;

        public Access(
                NamedLockFactory namedLockFactory,
                String name,
                boolean shared,
                CountDownLatch winner,
                CountDownLatch loser) {
            this.namedLockFactory = namedLockFactory;
            this.name = name;
            this.shared = shared;
            this.winner = winner;
            this.loser = loser;
        }

        @Override
        public void run() {
            try (NamedLock lock = namedLockFactory.getLock(name)) {
                if (shared
                        ? lock.lockShared(100L, TimeUnit.MILLISECONDS)
                        : lock.lockExclusively(100L, TimeUnit.MILLISECONDS)) {
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
