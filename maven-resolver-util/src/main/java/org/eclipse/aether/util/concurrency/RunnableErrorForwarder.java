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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;

/**
 * A utility class to forward any uncaught {@link Error} or {@link RuntimeException} from a {@link Runnable} executed in
 * a worker thread back to the parent thread. The simplified usage pattern looks like this:
 *
 * <pre>
 * RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();
 * for ( Runnable task : tasks )
 * {
 *     executor.execute( errorForwarder.wrap( task ) );
 * }
 * errorForwarder.await();
 * </pre>
 */
public final class RunnableErrorForwarder {

    private final Thread thread = Thread.currentThread();

    private final AtomicInteger counter = new AtomicInteger();

    private final AtomicReference<Throwable> error = new AtomicReference<>();

    /**
     * Creates a new error forwarder for worker threads spawned by the current thread.
     */
    public RunnableErrorForwarder() {}

    /**
     * Wraps the specified runnable into an equivalent runnable that will allow forwarding of uncaught errors.
     *
     * @param runnable The runnable from which to forward errors, must not be {@code null}.
     * @return The error-forwarding runnable to eventually execute, never {@code null}.
     */
    public Runnable wrap(final Runnable runnable) {
        requireNonNull(runnable, "runnable cannot be null");

        counter.incrementAndGet();

        return () -> {
            try {
                runnable.run();
            } catch (RuntimeException | Error e) {
                error.compareAndSet(null, e);
                throw e;
            } finally {
                counter.decrementAndGet();
                LockSupport.unpark(thread);
            }
        };
    }

    /**
     * Causes the current thread to wait until all previously {@link #wrap(Runnable) wrapped} runnables have terminated
     * and potentially re-throws an uncaught {@link RuntimeException} or {@link Error} from any of the runnables. In
     * case multiple runnables encountered uncaught errors, one error is arbitrarily selected. <em>Note:</em> This
     * method must be called from the same thread that created this error forwarder instance.
     */
    public void await() {
        awaitTerminationOfAllRunnables();

        Throwable error = this.error.get();
        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            } else if (error instanceof ThreadDeath) {
                throw new IllegalStateException(error);
            } else if (error instanceof Error) {
                throw (Error) error;
            }
            throw new IllegalStateException(error);
        }
    }

    private void awaitTerminationOfAllRunnables() {
        if (!thread.equals(Thread.currentThread())) {
            throw new IllegalStateException(
                    "wrong caller thread, expected " + thread + " and not " + Thread.currentThread());
        }

        boolean interrupted = false;

        while (counter.get() > 0) {
            LockSupport.park();

            if (Thread.interrupted()) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
