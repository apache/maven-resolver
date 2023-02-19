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
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Named lock support implementation that is using {@link ReadWriteLock} instances. The adapted lock MUST SUPPORT
 * reentrancy, non re-entrant locks will NOT work. It is the responsibility of an adapting lock, to ensure that
 * above lock requirement stands.
 */
public class ReadWriteLockNamedLock extends NamedLockSupport {
    private enum Step {
        /**
         * Step when {@link ReadWriteLock#readLock()} was locked
         */
        SHARED,

        /**
         * Step when {@link ReadWriteLock#writeLock()} was locked
         */
        EXCLUSIVE
    }

    private final ThreadLocal<Deque<Step>> threadSteps;

    private final ReadWriteLock readWriteLock;

    public ReadWriteLockNamedLock(
            final String name, final NamedLockFactorySupport factory, final ReadWriteLock readWriteLock) {
        super(name, factory);
        this.threadSteps = ThreadLocal.withInitial(ArrayDeque::new);
        this.readWriteLock = readWriteLock;
    }

    @Override
    public boolean lockShared(final long time, final TimeUnit unit) throws InterruptedException {
        Deque<Step> steps = threadSteps.get();
        if (readWriteLock.readLock().tryLock(time, unit)) {
            steps.push(Step.SHARED);
            return true;
        }
        return false;
    }

    @Override
    public boolean lockExclusively(final long time, final TimeUnit unit) throws InterruptedException {
        Deque<Step> steps = threadSteps.get();
        if (!steps.isEmpty()) { // we already own shared or exclusive lock
            if (!steps.contains(Step.EXCLUSIVE)) {
                return false; // Lock upgrade not supported
            }
        }
        if (readWriteLock.writeLock().tryLock(time, unit)) {
            steps.push(Step.EXCLUSIVE);
            return true;
        }
        return false;
    }

    @Override
    public void unlock() {
        Deque<Step> steps = threadSteps.get();
        if (steps.isEmpty()) {
            throw new IllegalStateException("Wrong API usage: unlock without lock");
        }
        Step step = steps.pop();
        if (Step.SHARED == step) {
            readWriteLock.readLock().unlock();
        } else if (Step.EXCLUSIVE == step) {
            readWriteLock.writeLock().unlock();
        }
    }
}
