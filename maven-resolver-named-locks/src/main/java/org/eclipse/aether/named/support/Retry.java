package org.eclipse.aether.named.support;

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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry helper: retries given {@code Callable} as long as it returns {@code null} (interpreted
 * as "no answer yet") or given time passes. This helper implements similar semantics regarding
 * caller threads as {@link java.util.concurrent.locks.Lock#tryLock(long, TimeUnit)} method does:
 * blocks the caller thread until operation return non-{@code null} value within the given waiting
 * time and the current thread has not been {@linkplain Thread#interrupt interrupted}.
 *
 * @since 1.7.3
 */
public final class Retry
{
    private static final Logger LOGGER = LoggerFactory.getLogger( Retry.class );

    private Retry()
    {
      // no instances
    }

    /**
     * Retries for given amount of time (time, unit) the passed in operation, sleeping given
     * {@code sleepMills} between retries. In case operation returns {@code null}, it is assumed
     * "is not done yet" state, so retry will happen (if time barrier allows). If time barrier
     * passes, and still {@code null} ("is not done yet") is returned from operation, the
     * {@code defaultResult} is returned.
     */
    public static  <R> R retry( final long time,
                                final TimeUnit unit,
                                final long sleepMillis,
                                final Callable<R> operation,
                                final Predicate<Exception> retryPredicate,
                                final R defaultResult ) throws InterruptedException
    {
        long now = System.nanoTime();
        final long barrier = now + unit.toNanos( time );
        int attempt = 1;
        R result = null;
        while ( now < barrier && result == null )
        {
          try
          {
            result = operation.call();
            if ( result == null )
            {
              LOGGER.trace( "Retry attempt {}: no result", attempt );
              Thread.sleep( sleepMillis );
            }
          }
          catch ( InterruptedException e )
          {
            throw e;
          }
          catch ( Exception e )
          {
            LOGGER.trace( "Retry attempt {}: operation failure", attempt, e );
            if ( retryPredicate != null && !retryPredicate.test( e ) )
            {
                throw new IllegalStateException( e );
            }
          }
          now = System.nanoTime();
          attempt++;
        }
        return result == null ? defaultResult : result;
    }

    /**
     * Retries attempting max given times the passed in operation, sleeping given
     * {@code sleepMills} between retries. In case operation returns {@code null}, it is assumed
     * "is not done yet" state, so retry will happen (if attempt count allows). If all attempts
     * used, and still {@code null} ("is not done yet") is returned from operation, the
     * {@code defaultResult} is returned.
     * <p>
     * Just to clear things up: 5 attempts is really 4 retries (once do it and retry 4 times). 0 attempts means
     * "do not even try it", and this method returns without doing anything.
     */
    public static  <R> R retry( final int attempts,
                                final long sleepMillis,
                                final Callable<R> operation,
                                final Predicate<Exception> retryPredicate,
                                final R defaultResult ) throws InterruptedException
    {
        int attempt = 1;
        R result = null;
        while ( attempt <= attempts && result == null )
        {
            try
            {
                result = operation.call();
                if ( result == null )
                {
                    LOGGER.trace( "Retry attempt {}: no result", attempt );
                    Thread.sleep( sleepMillis );
                }
            }
            catch ( InterruptedException e )
            {
                throw e;
            }
            catch ( Exception e )
            {
                LOGGER.trace( "Retry attempt {}: operation failure", attempt, e );
                if ( retryPredicate != null && !retryPredicate.test( e ) )
                {
                    throw new IllegalStateException( e );
                }
            }
            attempt++;
        }
        return result == null ? defaultResult : result;
    }
}
