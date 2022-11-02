package org.eclipse.aether.internal.impl.concurrency;

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

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.aether.spi.concurrency.ResolverExecutor;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link ResolverExecutor}.
 * <p>
 * It relies on ctor passed {@link ExecutorService} that may be {@code null}, in which case "direct invocation" (on
 * caller thread) happens, otherwise the non-null executor service is used.
 */
final class DefaultResolverExecutor implements ResolverExecutor
{
    private final ExecutorService executorService;

    DefaultResolverExecutor( final ExecutorService executorService )
    {
        this.executorService = executorService;
    }

    @Override
    public void submitBatch( Collection<Runnable> tasks )
    {
        requireNonNull( tasks );
        if ( tasks.size() == 1 )
        {
            directlyExecute( Executors.callable( tasks.iterator().next() ) );
        }
        else
        {
            for ( Runnable task : tasks )
            {
                submit( Executors.callable( task ) );
            }
        }
    }

    @Override
    public void submit( Runnable task )
    {
        requireNonNull( task );
        submit( Executors.callable( task ) );
    }

    @Override
    public <T> Future<T> submit( Callable<T> task )
    {
        requireNonNull( task );
        return executorService == null ? directlyExecute( task ) : executorService.submit( task );
    }

    private static <T> Future<T> directlyExecute( Callable<T> task )
    {
        CompletableFuture<T> future;
        try
        {
            future = CompletableFuture.completedFuture( task.call() );
        }
        catch ( Exception e )
        {
            future = new CompletableFuture<>();
            future.completeExceptionally( e );
        }
        return future;
    }
}