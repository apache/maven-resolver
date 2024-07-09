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
package org.eclipse.aether.named.ipc;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.support.LockUpgradeNotSupportedException;
import org.eclipse.aether.named.support.NamedLockSupport;

/**
 * IPC named locks.
 *
 * @since 2.0.1
 */
class IpcNamedLock extends NamedLockSupport {

    final IpcClient client;
    final Collection<String> keys;
    final ThreadLocal<ArrayDeque<Ctx>> contexts;

    IpcNamedLock(NamedLockKey key, IpcNamedLockFactory factory, IpcClient client, Collection<String> keys) {
        super(key, factory);
        this.client = client;
        this.keys = keys;
        this.contexts = ThreadLocal.withInitial(ArrayDeque::new);
    }

    @Override
    public boolean doLockShared(long time, TimeUnit unit) {
        ArrayDeque<Ctx> contexts = this.contexts.get();
        if (!contexts.isEmpty()) {
            contexts.push(new Ctx(false, null, true));
            return true;
        }
        try {
            String contextId = client.newContext(true, time, unit);
            client.lock(Objects.requireNonNull(contextId), keys, time, unit);
            contexts.push(new Ctx(true, contextId, true));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public boolean doLockExclusively(long time, TimeUnit unit) {
        ArrayDeque<Ctx> contexts = this.contexts.get();
        if (contexts.stream().anyMatch(c -> c.shared)) {
            throw new LockUpgradeNotSupportedException(this);
        }
        if (!contexts.isEmpty()) {
            contexts.push(new Ctx(false, null, false));
            return true;
        }
        try {
            String contextId = client.newContext(false, time, unit);
            client.lock(Objects.requireNonNull(contextId), keys, time, unit);
            contexts.push(new Ctx(true, contextId, false));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    @Override
    public void doUnlock() {
        ArrayDeque<Ctx> contexts = this.contexts.get();
        if (contexts.isEmpty()) {
            throw new IllegalStateException("improper boxing");
        }
        Ctx ctx = contexts.pop();
        if (ctx.acted) {
            client.unlock(ctx.contextId);
        }
    }

    private static final class Ctx {
        private final boolean acted;
        private final String contextId;
        private final boolean shared;

        private Ctx(boolean acted, String contextId, boolean shared) {
            this.acted = acted;
            this.contextId = contextId;
            this.shared = shared;
        }
    }
}
