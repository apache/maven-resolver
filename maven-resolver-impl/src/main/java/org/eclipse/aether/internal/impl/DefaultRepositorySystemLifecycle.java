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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession.CloseableRepositorySystemSession;
import org.eclipse.aether.impl.RepositorySystemLifecycle;

import static java.util.Objects.requireNonNull;

/**
 *
 */
@Singleton
@Named
public class DefaultRepositorySystemLifecycle implements RepositorySystemLifecycle {
    private final AtomicBoolean shutdown;

    private final CopyOnWriteArrayList<Runnable> onSystemEndedHandlers;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Runnable>> onSessionEndedHandlers;

    @Inject
    public DefaultRepositorySystemLifecycle() {
        this.shutdown = new AtomicBoolean(false);
        this.onSystemEndedHandlers = new CopyOnWriteArrayList<>();
        this.onSessionEndedHandlers = new ConcurrentHashMap<>();
    }

    @Override
    public void systemEnded() {
        if (shutdown.compareAndSet(false, true)) {
            final ArrayList<Exception> exceptions = new ArrayList<>();
            for (Runnable onCloseHandler : onSystemEndedHandlers) {
                try {
                    onCloseHandler.run();
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            MultiRuntimeException.mayThrow("system on-close handler failures", exceptions);
        }
    }

    @Override
    public void addOnSystemEndedHandler(Runnable handler) {
        requireNonNull(handler, "handler cannot be null");
        requireNotShutdown();
        onSystemEndedHandlers.add(0, handler);
    }

    @Override
    public void sessionStarted(CloseableRepositorySystemSession session) {
        requireNonNull(session, "session cannot be null");
        requireNotShutdown();
        String sessionId = session.sessionId();
        onSessionEndedHandlers.compute(sessionId, (k, v) -> {
            if (v != null) {
                throw new IllegalStateException("session instance already registered");
            }
            return new CopyOnWriteArrayList<>();
        });
    }

    @Override
    public void sessionEnded(CloseableRepositorySystemSession session) {
        requireNonNull(session, "session cannot be null");
        requireNotShutdown();
        String sessionId = session.sessionId();
        ArrayList<Runnable> handlers = new ArrayList<>();
        onSessionEndedHandlers.compute(sessionId, (k, v) -> {
            if (v == null) {
                throw new IllegalStateException("session instance not registered");
            }
            handlers.addAll(v);
            return null;
        });

        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Runnable handler : handlers) {
            try {
                handler.run();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        MultiRuntimeException.mayThrow("sessionEnded handler issue(s)", exceptions);
    }

    @Override
    public void addOnSessionEndedHandle(CloseableRepositorySystemSession session, Runnable handler) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(handler, "handler cannot be null");
        requireNotShutdown();
        String sessionId = session.sessionId();
        onSessionEndedHandlers.compute(sessionId, (k, v) -> {
            if (v == null) {
                throw new IllegalStateException("session instance not registered");
            }
            v.add(handler);
            return v;
        });
    }

    private void requireNotShutdown() {
        if (shutdown.get()) {
            throw new IllegalStateException("repository system is already shut down");
        }
    }
}
