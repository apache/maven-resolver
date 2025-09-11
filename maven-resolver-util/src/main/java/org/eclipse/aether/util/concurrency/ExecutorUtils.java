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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Utilities for executors and sizing them.
 *
 * @since 1.9.5
 * @deprecated for removal. Nothing is using this class within Resolver.
 */
@Deprecated
public final class ExecutorUtils {
    /**
     * Shared instance of "direct executor".
     */
    public static final Executor DIRECT_EXECUTOR = Runnable::run;

    /**
     * Creates new thread pool {@link ExecutorService}. The {@code poolSize} parameter but be greater than 1.
     */
    public static ExecutorService threadPool(int poolSize, String namePrefix) {
        if (poolSize < 2) {
            throw new IllegalArgumentException("Invalid poolSize: " + poolSize + ". Must be greater than 1.");
        }
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                3L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new WorkerThreadFactory(namePrefix));
    }

    /**
     * Returns {@link #DIRECT_EXECUTOR} or result of {@link #threadPool(int, String)} depending on value of
     * {@code size} parameter.
     */
    public static Executor executor(int size, String namePrefix) {
        if (size <= 1) {
            return DIRECT_EXECUTOR;
        } else {
            return threadPool(size, namePrefix);
        }
    }

    /**
     * To be used with result of {@link #executor(int, String)} method, shuts down instance if it is
     * {@link ExecutorService}.
     */
    public static void shutdown(Executor executor) {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
    }

    /**
     * Retrieves and validates requested thread count based on session and specified keys, or if none provided, the
     * provided default value. This method validates result on top of what {@link ConfigUtils} does.
     *
     * @throws IllegalArgumentException if default value is less than 1
     * @see ConfigUtils#getInteger(RepositorySystemSession, int, String...)
     */
    public static int threadCount(RepositorySystemSession session, int defaultValue, String... keys) {
        if (defaultValue < 1) {
            throw new IllegalArgumentException("Invalid defaultValue: " + defaultValue + ". Must be greater than 0.");
        }
        int threadCount = ConfigUtils.getInteger(session, defaultValue, keys);
        if (threadCount < 1) {
            throw new IllegalArgumentException("Invalid value: " + threadCount + ". Must be greater than 0.");
        }
        return threadCount;
    }
}
