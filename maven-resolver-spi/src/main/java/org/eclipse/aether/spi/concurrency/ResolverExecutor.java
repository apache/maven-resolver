package org.eclipse.aether.spi.concurrency;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Component providing {@link java.util.concurrent.Executor}-like service across resolver. Instances are to be treated
 * like resources, best in try-with-resource constructs.
 *
 * @since 1.9.0
 */
public interface ResolverExecutor extends Closeable
{
    /**
     * Submits a batch of {@link Runnable} tasks for execution. If collection has size greater than 1, this method
     * will submit all tasks just like {@link #submit(Callable)} does. Otherwise, "direct", on caller thread execution
     * happens. Several resolver components may deal "sequentially" with tasks and rely on this behaviour for
     * performance purposes.
     * <p>
     * Error handling: this method will never throw. If you are interested in possible outcome of submitted
     * {@link Runnable} use some helper like the {@code RunnableErrorForwarder} in resolver utilities module.
     */
    void submitBatch( Collection<Runnable> batch );

    /**
     * Submits a {@link Callable} task for execution. This call may block if thread pool is full. In certain
     * circumstances this method may choose to directly invoke task (on caller thread) instead to submit it.
     * <p>
     * Error handling: this method will never throw.
     */
    <T> Future<T> submit( Callable<T> task );

    /**
     * Caller notifies that is not using this instance anymore, it is up to implementation to shut it down, or do
     * whatever is needed.
     */
    void close();
}
