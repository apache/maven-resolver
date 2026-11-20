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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.support.LockUpgradeNotSupportedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * UT support for {@link NamedLockFactory}.
 */
public abstract class NamedLockFactoryTestSupport {
    protected static final HazelcastClientUtils UTILS = new HazelcastClientUtils();

    protected static NamedLockFactory namedLockFactory;

    private String testName;

    @AfterAll
    public static void cleanup() {
        if (namedLockFactory != null) {
            namedLockFactory.shutdown();
        }
        UTILS.cleanup();
    }

    @Test
    public void refCounting() {
        final String name = testName;
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
    public void unlockWoLock() {
        assertThrows(IllegalStateException.class, () -> {
            final String name = testName;
            try (NamedLock one = namedLockFactory.getLock(name)) {
                one.unlock();
            }
        });
    }

    @Test
    public void wwBoxing() throws InterruptedException {
        final String name = testName;
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rrBoxing() throws InterruptedException {
        final String name = testName;
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void wrBoxing() throws InterruptedException {
        final String name = testName;
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockExclusively(1L, TimeUnit.MILLISECONDS));
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rwBoxing() throws InterruptedException {
        final String name = testName;
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertTrue(one.lockShared(1L, TimeUnit.MILLISECONDS));
            try {
                one.lockExclusively(1L, TimeUnit.MILLISECONDS);
            } catch (LockUpgradeNotSupportedException e) {
                // good
            }
            one.unlock();
        }
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void sharedAccess() throws InterruptedException {
        final String name = testName;
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
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void exclusiveAccess() throws InterruptedException {
        final String name = testName;
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
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void mixedAccess() throws InterruptedException {
        final String name = testName;
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

        Access(
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

    @BeforeEach
    public void setup(TestInfo testInfo) {
        Optional<Method> testMethod = testInfo.getTestMethod();
        if (testMethod.isPresent()) {
            this.testName = testMethod.get().getName();
        }
    }
}
