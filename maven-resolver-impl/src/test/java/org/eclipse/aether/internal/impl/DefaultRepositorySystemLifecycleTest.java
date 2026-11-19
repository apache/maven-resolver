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
package org.eclipse.aether.internal.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRepositorySystemLifecycleTest {

    private static CloseableSession session(String id) {
        CloseableSession session = mock(CloseableSession.class);
        when(session.sessionId()).thenReturn(id);
        return session;
    }

    @Test
    void sessionEndedRunsItsHandlersOnce() {
        DefaultRepositorySystemLifecycle lifecycle = new DefaultRepositorySystemLifecycle();
        CloseableSession session = session("s1");
        AtomicInteger runs = new AtomicInteger();
        lifecycle.sessionStarted(session);
        lifecycle.addOnSessionEndedHandle(session, runs::incrementAndGet);

        lifecycle.sessionEnded(session);
        lifecycle.systemEnded();

        assertEquals(1, runs.get());
    }

    @Test
    void sessionClosedAfterShutdownDoesNotThrowOrRunItsHandlersAgain() {
        DefaultRepositorySystemLifecycle lifecycle = new DefaultRepositorySystemLifecycle();
        CloseableSession session = session("s1");
        AtomicInteger runs = new AtomicInteger();
        lifecycle.sessionStarted(session);
        lifecycle.addOnSessionEndedHandle(session, runs::incrementAndGet);

        // shutting down with the session still open reports it, and runs its handlers
        assertThrows(MultiRuntimeException.class, lifecycle::systemEnded);
        assertEquals(1, runs.get());

        assertDoesNotThrow(() -> lifecycle.sessionEnded(session));
        assertEquals(1, runs.get());
    }

    @Test
    void sessionCannotStartAfterShutdown() {
        DefaultRepositorySystemLifecycle lifecycle = new DefaultRepositorySystemLifecycle();
        lifecycle.systemEnded();

        assertThrows(IllegalStateException.class, () -> lifecycle.sessionStarted(session("s1")));
    }
}
