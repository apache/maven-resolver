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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of composite lock when "composition" is needed for locks that are naturally mapped as 1:1 name
 * vs some backing implementation. Instances of these locks are "unique per call" and are not ref counted.
 *
 * @since 2.0.0
 */
public final class CompositeNamedLockImpl extends NamedLockSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeNamedLockImpl.class);

    private final Map<NamedLockKey, NamedLock> locks;

    private final ArrayDeque<ArrayDeque<NamedLock>> steps = new ArrayDeque<>();

    public CompositeNamedLockImpl(NamedLockKey key, NamedLockFactorySupport factory, Collection<NamedLock> namedLocks) {
        super(key, factory);
        LinkedHashMap<NamedLockKey, NamedLock> map = new LinkedHashMap<>();
        namedLocks.forEach(l -> map.put(l.key(), l));
        this.locks = Collections.unmodifiableMap(map);
    }

    @Override
    protected boolean doLockShared(long time, TimeUnit unit) throws InterruptedException {
        return lock(time, unit, true);
    }

    @Override
    protected boolean doLockExclusively(long time, TimeUnit unit) throws InterruptedException {
        return lock(time, unit, false);
    }

    private boolean lock(long time, TimeUnit timeUnit, boolean shared) throws InterruptedException {
        final ArrayDeque<NamedLock> step = new ArrayDeque<>(locks.size());
        final String timeStr = time + " " + timeUnit;
        final String lockKind = shared ? "shared" : "exclusive";
        LOGGER.trace("{}: Need {} {} lock(s) in {}", key(), locks.size(), lockKind, timeStr);
        for (NamedLock namedLock : locks.values()) {
            LOGGER.trace("{}: Acquiring {} lock for '{}'", key(), lockKind, namedLock.key());

            boolean locked;
            if (shared) {
                locked = namedLock.lockShared(time, timeUnit);
            } else {
                locked = namedLock.lockExclusively(time, timeUnit);
            }

            if (!locked) {
                LOGGER.trace("{}: Failed to acquire {} lock for '{}' in {}", key(), lockKind, namedLock.key(), timeStr);

                unlockAll(step);
                break;
            } else {
                step.push(namedLock);
            }
        }
        if (step.size() == locks.size()) {
            steps.push(step);
            return true;
        }
        unlockAll(step);
        return false;
    }

    @Override
    protected void doUnlock() {
        unlockAll(steps.pop());
    }

    @Override
    protected void doClose() {
        locks.values().forEach(NamedLock::close);
    }

    private void unlockAll(final ArrayDeque<NamedLock> locks) {
        if (locks.isEmpty()) {
            return;
        }

        // Release locks in reverse locking order
        while (!locks.isEmpty()) {
            NamedLock namedLock = locks.pop();
            LOGGER.trace("{}: Releasing lock for '{}'", key(), namedLock.key());
            namedLock.unlock();
        }
    }
}
