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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * UT support for {@link NamedLockFactory}.
 */
public abstract class NamedLockFactoryTestSupport {

    protected static NamedLockFactory namedLockFactory;

    @Rule
    public TestName testName = new TestName();

    protected String lockName() {
        return testName.getMethodName();
    }

    @Test
    public void refCounting() {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name);
                NamedLock two = namedLockFactory.getLock(name)) {
            assertThat(one, sameInstance(two));
            one.close();
            two.close();

            try (NamedLock three = namedLockFactory.getLock(name)) {
                assertThat(three, not(sameInstance(two)));
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void unlockWoLock() {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            one.unlock();
        }
    }

    @Test
    public void wwBoxing() throws InterruptedException {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rrBoxing() throws InterruptedException {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void wrBoxing() throws InterruptedException {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rwBoxing() throws InterruptedException {
        final String name = lockName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(false));
            one.unlock();
        }
    }

    @Test(timeout = 5000)
    public void sharedAccess() throws InterruptedException {
        final String name = lockName();
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
        Thread t2 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void exclusiveAccess() throws InterruptedException {
        final String name = lockName();
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

    @Test(timeout = 5000)
    public void mixedAccess() throws InterruptedException {
        final String name = lockName();
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

    @Test(timeout = 5000)
    public void fullyConsumeLockTime() throws InterruptedException {
        long start = System.nanoTime();
        final String name = lockName();
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
        long end = System.nanoTime();
        long duration = end - start;
        long expectedDuration = TimeUnit.MILLISECONDS.toNanos(ACCESS_WAIT_MILLIS);
        assertThat(duration, greaterThanOrEqualTo(expectedDuration)); // equal in ideal case
    }

    @Test(timeout = 5000)
    public void releasedExclusiveAllowAccess() throws InterruptedException {
        final String name = lockName();
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 loser
        Thread t1 = new Thread(new Access(namedLockFactory, name, true, winners, losers));
        try (NamedLock namedLock = namedLockFactory.getLock(name)) {
            assertThat(namedLock.lockExclusively(50L, TimeUnit.MILLISECONDS), is(true));
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
                Assert.fail(e.getMessage());
            }
        }
    }
}
