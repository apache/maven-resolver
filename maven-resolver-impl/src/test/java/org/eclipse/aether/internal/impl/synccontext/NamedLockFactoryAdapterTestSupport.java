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
package org.eclipse.aether.internal.impl.synccontext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.synccontext.named.*;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT support for {@link SyncContextFactory}.
 */
public abstract class NamedLockFactoryAdapterTestSupport {
    private static final long ADAPTER_TIME = 1000L;

    private static final TimeUnit ADAPTER_TIME_UNIT = TimeUnit.MILLISECONDS;

    /**
     * Subclass MAY populate this field but subclass must take care of proper cleanup as well, if needed!
     */
    protected static NameMapper nameMapper = new DiscriminatingNameMapper(GAVNameMapper.gav());

    /**
     * Subclass MUST populate this field but subclass must take care of proper cleanup as well, if needed! Once set,
     * subclass must MUST call {@link #createAdapter()}.
     */
    protected static NamedLockFactory namedLockFactory;

    private static NamedLockFactoryAdapter adapter;

    private RepositorySystemSession session;

    public static void createAdapter() {
        Objects.requireNonNull(namedLockFactory, "NamedLockFactory not set");
        adapter = new NamedLockFactoryAdapter(nameMapper, namedLockFactory);
    }

    @AfterClass
    public static void cleanupAdapter() {
        if (adapter != null) {
            adapter.getNamedLockFactory().shutdown();
        }
    }

    @Before
    public void before() throws IOException {
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"))); // hack for Surefire
        LocalRepository localRepository =
                new LocalRepository(Files.createTempDirectory("test").toFile());
        session = mock(RepositorySystemSession.class);
        when(session.getLocalRepository()).thenReturn(localRepository);
        HashMap<String, Object> config = new HashMap<>();
        config.put(NamedLockFactoryAdapter.TIME_KEY, String.valueOf(ADAPTER_TIME));
        config.put(NamedLockFactoryAdapter.TIME_UNIT_KEY, ADAPTER_TIME_UNIT.name());
        when(session.getConfigProperties()).thenReturn(config);
    }

    @Test
    public void justCreateAndClose() {
        adapter.newInstance(session, false).close();
    }

    @Test
    public void justAcquire() {
        try (SyncContext syncContext = adapter.newInstance(session, false)) {
            syncContext.acquire(
                    Arrays.asList(
                            new DefaultArtifact("groupId:artifactId:1.0"),
                            new DefaultArtifact("groupId:artifactId:1.1")),
                    null);
        }
    }

    @Test(timeout = 5000)
    public void sharedAccess() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(true, winners, losers, adapter, session, null));
        Thread t2 = new Thread(new Access(true, winners, losers, adapter, session, null));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void exclusiveAccess() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(false, winners, losers, adapter, session, null));
        Thread t2 = new Thread(new Access(false, winners, losers, adapter, session, null));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void mixedAccess() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(true, winners, losers, adapter, session, null));
        Thread t2 = new Thread(new Access(false, winners, losers, adapter, session, null));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void nestedSharedShared() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(
                true, winners, losers, adapter, session, new Access(true, winners, losers, adapter, session, null)));
        t1.start();
        t1.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void nestedExclusiveShared() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(
                false, winners, losers, adapter, session, new Access(true, winners, losers, adapter, session, null)));
        t1.start();
        t1.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void nestedExclusiveExclusive() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(2); // we expect 2 winners
        CountDownLatch losers = new CountDownLatch(0); // we expect 0 losers
        Thread t1 = new Thread(new Access(
                false, winners, losers, adapter, session, new Access(false, winners, losers, adapter, session, null)));
        t1.start();
        t1.join();
        winners.await();
        losers.await();
    }

    @Test(timeout = 5000)
    public void nestedSharedExclusive() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner (outer)
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser (inner)
        Thread t1 = new Thread(new Access(
                true, winners, losers, adapter, session, new Access(false, winners, losers, adapter, session, null)));
        t1.start();
        t1.join();
        winners.await();
        losers.await();
    }

    @Test
    public void fullyConsumeLockTime() throws InterruptedException {
        long start = System.nanoTime();
        CountDownLatch winners = new CountDownLatch(1); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(1); // we expect 1 loser
        Thread t1 = new Thread(new Access(false, winners, losers, adapter, session, null));
        Thread t2 = new Thread(new Access(false, winners, losers, adapter, session, null));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        winners.await();
        losers.await();
        long end = System.nanoTime();
        long duration = end - start;
        long expectedDuration = ADAPTER_TIME_UNIT.toNanos(ADAPTER_TIME);
        assertThat(duration, greaterThanOrEqualTo(expectedDuration)); // equal in ideal case
    }

    @Test
    public void releasedExclusiveAllowAccess() throws InterruptedException {
        CountDownLatch winners = new CountDownLatch(2); // we expect 1 winner
        CountDownLatch losers = new CountDownLatch(0); // we expect 1 loser
        Thread t1 = new Thread(new Access(false, winners, losers, adapter, session, null));
        new Access(false, winners, losers, adapter, session, null).run();
        t1.start();
        t1.join();
        winners.await();
        losers.await();
    }

    private static class Access implements Runnable {
        final boolean shared;
        final CountDownLatch winner;
        final CountDownLatch loser;
        final NamedLockFactoryAdapter adapter;
        final RepositorySystemSession session;
        final Access chained;

        public Access(
                boolean shared,
                CountDownLatch winner,
                CountDownLatch loser,
                NamedLockFactoryAdapter adapter,
                RepositorySystemSession session,
                Access chained) {
            this.shared = shared;
            this.winner = winner;
            this.loser = loser;
            this.adapter = adapter;
            this.session = session;
            this.chained = chained;
        }

        @Override
        public void run() {
            try {
                try (SyncContext syncContext = adapter.newInstance(session, shared)) {
                    syncContext.acquire(
                            Arrays.asList(
                                    new DefaultArtifact("groupId:artifactId:1.0"),
                                    new DefaultArtifact("groupId:artifactId:1.1")),
                            null);
                    winner.countDown();
                    if (chained != null) {
                        chained.run();
                    }
                    loser.await();
                } catch (IllegalStateException e) {
                    e.printStackTrace(); // for ref purposes
                    loser.countDown();
                    winner.await();
                }
            } catch (InterruptedException e) {
                Assert.fail("interrupted");
            }
        }
    }
}
