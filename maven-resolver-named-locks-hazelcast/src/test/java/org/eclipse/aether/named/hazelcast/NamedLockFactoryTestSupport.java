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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * UT support for {@link NamedLockFactory}.
 */
public abstract class NamedLockFactoryTestSupport {
    protected static final HazelcastClientUtils utils = new HazelcastClientUtils();

    protected static NamedLockFactory namedLockFactory;

    @Rule
    public TestName testName = new TestName();

    @AfterClass
    public static void cleanup() {
        if (namedLockFactory != null) {
            namedLockFactory.shutdown();
        }
        utils.cleanup();
    }

    @Test
    public void refCounting() {
        final String name = testName.getMethodName();
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
        final String name = testName.getMethodName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            one.unlock();
        }
    }

    @Test
    public void wwBoxing() throws InterruptedException {
        final String name = testName.getMethodName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rrBoxing() throws InterruptedException {
        final String name = testName.getMethodName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void wrBoxing() throws InterruptedException {
        final String name = testName.getMethodName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            one.unlock();
            one.unlock();
        }
    }

    @Test
    public void rwBoxing() throws InterruptedException {
        final String name = testName.getMethodName();
        try (NamedLock one = namedLockFactory.getLock(name)) {
            assertThat(one.lockShared(1L, TimeUnit.MILLISECONDS), is(true));
            assertThat(one.lockExclusively(1L, TimeUnit.MILLISECONDS), is(false));
            one.unlock();
        }
    }

    @Test(timeout = 5000)
    public void sharedAccess() throws InterruptedException {
        final String name = testName.getMethodName();
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

    @Test(timeout = 5000)
    public void exclusiveAccess() throws InterruptedException {
        final String name = testName.getMethodName();
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
        final String name = testName.getMethodName();
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
                Assert.fail(e.getMessage());
            }
        }
    }
}
