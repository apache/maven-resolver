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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.synccontext.named.NameMappers;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UT for retry handling of {@link NamedLockFactoryAdapter}: a lock acquired on a retry attempt must not be reported as
 * failure.
 */
public class NamedLockFactoryAdapterRetryTest {
    private static final int RETRY = 2;

    private RepositorySystemSession session;

    @Before
    public void before() {
        session = mock(RepositorySystemSession.class);
        when(session.getLocalRepository()).thenReturn(new LocalRepository("target/local-repo"));
        HashMap<String, Object> config = new HashMap<>();
        config.put(NamedLockFactoryAdapter.TIME_KEY, "1");
        config.put(NamedLockFactoryAdapter.TIME_UNIT_KEY, TimeUnit.MILLISECONDS.name());
        config.put(NamedLockFactoryAdapter.RETRY_KEY, String.valueOf(RETRY));
        config.put(NamedLockFactoryAdapter.RETRY_WAIT_KEY, "1");
        when(session.getConfigProperties()).thenReturn(config);
    }

    @Test
    public void lockAcquiredOnRetryIsNotAFailure() {
        ScriptedLockFactory factory = new ScriptedLockFactory(2); // timeout on attempts 1 and 2, succeed on 3
        NamedLockFactoryAdapter adapter = new NamedLockFactoryAdapter(NameMappers.gavNameMapper(), factory);

        try (SyncContext syncContext = adapter.newInstance(session, false)) {
            syncContext.acquire(Arrays.asList(new DefaultArtifact("g:a:1.0")), null);
            assertEquals(3, factory.lockAttempts);
            assertEquals(1, factory.heldLocks());
        }
        assertEquals(0, factory.heldLocks());
    }

    @Test
    public void partiallyAcquiredLocksAreReleasedBeforeRetry() {
        ScriptedLockFactory factory = new ScriptedLockFactory(0);
        factory.failOnce("artifact:g:b:1.0"); // second of two names times out on attempt 1 only
        NamedLockFactoryAdapter adapter = new NamedLockFactoryAdapter(NameMappers.gavNameMapper(), factory);

        try (SyncContext syncContext = adapter.newInstance(session, false)) {
            syncContext.acquire(Arrays.asList(new DefaultArtifact("g:a:1.0"), new DefaultArtifact("g:b:1.0")), null);
            assertEquals(2, factory.heldLocks());
        }
        assertEquals(0, factory.heldLocks());
    }

    /**
     * Factory whose locks time out the first {@code timeouts} times any lock is requested, and the first time for any
     * lock registered with {@link #failOnce(String)}.
     */
    private static class ScriptedLockFactory implements NamedLockFactory {
        private final List<String> failOnce = new ArrayList<>();

        private final List<ScriptedLock> locks = new ArrayList<>();

        private int remainingTimeouts;

        int lockAttempts;

        ScriptedLockFactory(int timeouts) {
            this.remainingTimeouts = timeouts;
        }

        void failOnce(String name) {
            failOnce.add(name);
        }

        int heldLocks() {
            return (int) locks.stream().filter(l -> l.held).count();
        }

        @Override
        public NamedLock getLock(String name) {
            ScriptedLock lock = new ScriptedLock(name);
            locks.add(lock);
            return lock;
        }

        @Override
        public void shutdown() {}

        private class ScriptedLock implements NamedLock {
            private final String name;

            boolean held;

            ScriptedLock(String name) {
                this.name = name;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean lockShared(long time, TimeUnit unit) {
                return lock();
            }

            @Override
            public boolean lockExclusively(long time, TimeUnit unit) {
                return lock();
            }

            private boolean lock() {
                lockAttempts++;
                if (failOnce.remove(name)) {
                    return false;
                }
                if (remainingTimeouts > 0) {
                    remainingTimeouts--;
                    return false;
                }
                held = true;
                return true;
            }

            @Override
            public void unlock() {
                held = false;
            }

            @Override
            public void close() {
                held = false;
            }
        }
    }
}
