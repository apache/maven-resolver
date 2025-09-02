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
package org.eclipse.aether.util.concurrency;

import java.util.concurrent.Executors;

import org.eclipse.aether.RepositorySystemSession;

import static java.util.Objects.requireNonNull;

/**
 * Utilities for executors and sizing them.
 * <em>Big fat note:</em> Do not use this class outside of resolver. This and related classes are not meant as "drop
 * in replacement" for Jave Executors, is used in very controlled fashion only.
 *
 * @since 2.0.11
 */
public final class SmartExecutorUtils {
    private static final SmartExecutor DIRECT = new SmartExecutor.Direct();

    private SmartExecutorUtils() {}

    /**
     * Returns a smart executor for given parameters. If {@code tasks} is known (non-null), it must be greater than 0.
     * The {@code maxConcurrentTasks} also must be greater than 0. The {@code namePrefix} must be non-null.
     * <p>
     * If {@code tasks} is set (is known), and equals to 1 (one), or {@code maxConcurrentTasks} equals to 1, the
     * {@link #DIRECT} executor is returned, otherwise pooled one.
     * <p>
     * If @code tasks} is not set (is null), pooled one is returned with pool size of {@code maxConcurrentTasks}. In
     * this case caller is advised to <em>reuse created executor across session</em>.
     * <p>
     * The returned instance must be closed out, ideally in try-with-resources construct. Returned instances when
     * {@code tasks} parameter is given should not be cached (like in a session) as they may return {@link #DIRECT}
     * executor for one call and a pool for subsequent call, based on value of tasks.
     *
     * @param tasks The amount of tasks, if known, {@code null} otherwise.
     * @param maxConcurrentTasks The maximum concurrency caller wants.
     * @param namePrefix The thread name prefixes, must not be {@code null).}
     */
    public static SmartExecutor newSmartExecutor(Integer tasks, int maxConcurrentTasks, String namePrefix) {
        if (maxConcurrentTasks < 1) {
            throw new IllegalArgumentException("maxConcurrentTasks must be > 0");
        }
        requireNonNull(namePrefix);
        int poolSize;
        if (tasks != null) {
            if (tasks < 1) {
                throw new IllegalArgumentException("tasks must be > 0");
            }
            if (tasks == 1 || maxConcurrentTasks == 1) {
                return DIRECT;
            }
            poolSize = Math.min(tasks, maxConcurrentTasks);
        } else {
            if (maxConcurrentTasks == 1) {
                return DIRECT;
            }
            poolSize = maxConcurrentTasks;
        }
        return new SmartExecutor.Pooled(Executors.newFixedThreadPool(poolSize, new WorkerThreadFactory(namePrefix)));
    }

    /**
     * Returns a smart executor, bound to session if tasks to execute are not known ahead of time. The returned
     * instance should be handled transparently, so preferably in try-with-resource even if underlying executor is
     * probably tied to session lifecycle, if applicable.
     * <p>
     * Implementation note: by this change, the caller "concurrency" is made deterministic and global(!). If you consider
     * collector example, it is called from project builder that in Maven 4 is already multithreaded, and before this
     * change the actual threads doing IO (HTTP) was {@code callerThreadCount x maxConcurrentTask} per JVM/Maven process.
     * Now, the {@code maxConcurrentTask} becomes global limit, and hence can be upped without unexpected "explosion"
     * in increasing build threading or anything.
     */
    public static SmartExecutor smartExecutor(
            RepositorySystemSession session, Integer tasks, int maxConcurrentTasks, String namePrefix) {
        if (tasks == null) {
            return (SmartExecutor)
                    session.getData().computeIfAbsent(SmartExecutor.class.getSimpleName() + "-" + namePrefix, () -> {
                        SmartExecutor smartExecutor = newSmartExecutor(null, maxConcurrentTasks, namePrefix);
                        session.addOnSessionEndedHandler(smartExecutor::close);
                        return new SmartExecutor.NonClosing(smartExecutor);
                    });
        } else {
            return newSmartExecutor(tasks, maxConcurrentTasks, namePrefix);
        }
    }
}
