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
package org.eclipse.aether.named.support;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for {@link NamedLock} implementations providing reference counting.
 */
public abstract class NamedLockSupport implements NamedLock {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final String name;

    private final NamedLockFactorySupport factory;

    private final ConcurrentHashMap<Thread, Deque<String>> diagnosticState; // non-null only if diag enabled

    public NamedLockSupport(final String name, final NamedLockFactorySupport factory) {
        this.name = name;
        this.factory = factory;
        this.diagnosticState = factory.isDiagnosticEnabled() ? new ConcurrentHashMap<>() : null;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean lockShared(long time, TimeUnit unit) throws InterruptedException {
        if (diagnosticState != null) {
            diagnosticState
                    .computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>())
                    .push("shared");
        }
        return doLockShared(time, unit);
    }

    protected abstract boolean doLockShared(long time, TimeUnit unit) throws InterruptedException;

    @Override
    public boolean lockExclusively(long time, TimeUnit unit) throws InterruptedException {
        if (diagnosticState != null) {
            diagnosticState
                    .computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>())
                    .push("exclusive");
        }
        return doLockExclusively(time, unit);
    }

    protected abstract boolean doLockExclusively(long time, TimeUnit unit) throws InterruptedException;

    @Override
    public void unlock() {
        doUnlock();
        if (diagnosticState != null) {
            diagnosticState
                    .computeIfAbsent(Thread.currentThread(), k -> new ArrayDeque<>())
                    .pop();
        }
    }

    protected abstract void doUnlock();

    @Override
    public void close() {
        doClose();
    }

    protected void doClose() {
        factory.closeLock(name);
    }

    /**
     * Returns the diagnostic state (if collected) or empty map, never {@code null}.
     *
     * @since TBD
     */
    public Map<Thread, Deque<String>> diagnosticState() {
        if (diagnosticState != null) {
            return diagnosticState;
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "name='" + name + '\'' + '}';
    }
}
