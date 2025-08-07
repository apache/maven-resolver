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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Utilities for executors and sizing them.
 * <em>Big fat note:</em> Do not use this class outside of resolver. This and related classes are not meant as "drop
 * in replacement" for Jave Executors, is used in very controlled fashion only.
 *
 * @since 2.0.11
 */
public interface SmartExecutor extends AutoCloseable {
    /**
     * Submits a {@link Runnable} to execution.
     */
    default void submit(Runnable runnable) {
        submit(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Submits a {@link Callable} to execution, returns a {@link CompletableFuture}.
     */
    <T> CompletableFuture<T> submit(Callable<T> callable);

    /**
     * Shut down this instance (ideally used in try-with-resource construct).
     */
    void close();

    /**
     * Direct executor (caller executes).
     */
    class Direct implements SmartExecutor {
        @Override
        public void submit(Runnable runnable) {
            runnable.run();
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> callable) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

        @Override
        public void close() {}
    }

    /**
     * Pooled executor backed by {@link ExecutorService}.
     */
    class Pooled implements SmartExecutor {
        private final ExecutorService executor;

        Pooled(ExecutorService executor) {
            this.executor = executor;
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> callable) {
            CompletableFuture<T> future = new CompletableFuture<>();
            executor.submit(() -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }

        @Override
        public void close() {
            executor.shutdown();
        }
    }

    /**
     * Limited executor, where the actual goal is to protect accessed resource.
     */
    class Limited implements SmartExecutor {
        private final ExecutorService executor;
        private final Semaphore semaphore;

        Limited(ExecutorService executor, int limit) {
            this.executor = executor;
            this.semaphore = new Semaphore(limit);
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> callable) {
            try {
                semaphore.acquire();
                CompletableFuture<T> future = new CompletableFuture<>();
                executor.submit(() -> {
                    try {
                        future.complete(callable.call());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    } finally {
                        semaphore.release();
                    }
                });
                return future;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            }
        }

        @Override
        public void close() {
            executor.shutdown();
        }
    }

    /**
     * Wrapper to prevent closing.
     */
    class NonClosing implements SmartExecutor {
        private final SmartExecutor smartExecutor;

        NonClosing(SmartExecutor smartExecutor) {
            this.smartExecutor = smartExecutor;
        }

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> callable) {
            return smartExecutor.submit(callable);
        }

        @Override
        public void close() {
            // nope; delegate is managed
        }
    }
}
