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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.MultiRuntimeException;
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

    @Inject
    public DefaultRepositorySystemLifecycle() {
        this.shutdown = new AtomicBoolean(false);
        this.onSystemEndedHandlers = new CopyOnWriteArrayList<>();
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

    private void requireNotShutdown() {
        if (shutdown.get()) {
            throw new IllegalStateException("repository system is already shut down");
        }
    }
}
