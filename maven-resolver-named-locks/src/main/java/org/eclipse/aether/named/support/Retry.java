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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry helper: retries operation for given time. Assumes {@code null} as "no answer". It retries as long as it
 * gets non-null result from operation, or the given time passes.
 *
 * @since TBD
 */
public final class Retry
{
    private static final long DEFAULT_SLEEP_MILLIS = 100L;

    private static final Logger LOGGER = LoggerFactory.getLogger( Retry.class );

    private Retry()
    {
      // no instances
    }

    /**
     * Same as {@link #retry(long, TimeUnit, long, Supplier, Object)} but uses {@link #DEFAULT_SLEEP_MILLIS} for
     * {@code sleepMillis} parameter.
     */
    public static <R> R retry( final long time,
                               final TimeUnit unit,
                               final Supplier<R> operation,
                               final R defaultResult ) throws InterruptedException
    {
        return retry( time, unit, DEFAULT_SLEEP_MILLIS, operation, defaultResult );
    }

    /**
     * Retries for given amount of time (time, unit) the passed in operation, sleeping given sleepMills between
     * retries. In case operation returns null, it is assumed "is not done yet" state, so retry will happen (if time
     * barrier allows). If time barrier passed, and still "is not done yet" (null) results arrive from operation,
     * the defaultResult is returned.
     */
    public static  <R> R retry( final long time,
                                final TimeUnit unit,
                                final long sleepMillis,
                                final Supplier<R> operation,
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
            result = operation.get();
            if ( result == null )
            {
              LOGGER.trace( "Attempt {}: no result", attempt );
              Thread.sleep( sleepMillis );
            }
          }
          catch ( InterruptedException e )
          {
            throw e;
          }
          catch ( Exception e )
          {
            LOGGER.trace( "Attempt {}: operation failure", attempt, e );
            throw new IllegalStateException( e );
          }
          now = System.nanoTime();
          attempt++;
        }
        return result == null ? defaultResult : result;
    }
}