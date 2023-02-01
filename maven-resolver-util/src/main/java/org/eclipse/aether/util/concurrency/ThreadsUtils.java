package org.eclipse.aether.util.concurrency;

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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Utilities for handling threads and pools.
 *
 * @since 1.9.5
 */
public final class ThreadsUtils
{
    /**
     * Shared instance of "direct executor".
     */
    public static final Executor DIRECT_EXECUTOR = Runnable::run;

    /**
     * Creates new thread pool {@link ExecutorService}. The {@code poolSize} parameter but be greater than 1.
     */
    public static ExecutorService threadPool( int poolSize, String namePrefix )
    {
        if ( poolSize < 2 )
        {
            throw new IllegalArgumentException(
                    "Invalid poolSize: " + poolSize + ". Must be greater than 1." );
        }
        return new ThreadPoolExecutor( poolSize, poolSize, 3L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new WorkerThreadFactory( namePrefix )
        );
    }

    /**
     * Returns {@link #DIRECT_EXECUTOR} or result of {@link #threadPool(int, String)} depending on value of
     * {@code size} parameter.
     */
    public static Executor executor( int size, String namePrefix )
    {
        if ( size <= 1 )
        {
            return DIRECT_EXECUTOR;
        }
        else
        {
            return threadPool( size, namePrefix );
        }
    }

    /**
     * To be used with result of {@link #executor(int, String)} method, shuts down instance if it is
     * {@link ExecutorService}.
     */
    public static void shutdown( Executor executor )
    {
        if ( executor instanceof ExecutorService )
        {
            ( (ExecutorService) executor ).shutdown();
        }
    }

    /**
     * Calculates requested thread count based on user configuration, or if none provided, the provided default value.
     *
     * @throws IllegalArgumentException if default value is less than 1.
     */
    public static int threadCount( RepositorySystemSession session, int defaultValue, String... keys )
    {
        if ( defaultValue < 1 )
        {
            throw new IllegalArgumentException(
                    "Invalid defaultValue: " + defaultValue + ". Must be positive." );
        }
        String threadConfiguration = ConfigUtils.getString( session, Integer.toString( defaultValue ), keys );
        try
        {
            return calculateDegreeOfConcurrency( threadConfiguration );
        }
        catch ( IllegalArgumentException e )
        {
            return defaultValue;
        }
    }

    /**
     * Calculates requested thread count based on user configuration, or if none provided, the provided default value.
     * The default value is string and supports expressions like "1C".
     *
     * @throws IllegalArgumentException if default value is invalid.
     */
    public static int threadCount( RepositorySystemSession session, String defaultValue, String... keys )
    {
        return threadCount( session, calculateDegreeOfConcurrency( defaultValue ), keys );
    }

    /**
     * Calculates "degree of concurrency" (count of threads to be used) based on non-null input string. String may
     * be string representation of integer or a string representation of float followed by "C" character
     * (case-sensitive) in which case the float is interpreted as multiplier for core count as reported by Java.
     *
     * Blatantly copied (and simplified) from maven-embedder
     * {@code org.apache.maven.cli.MavenCli#calculateDegreeOfConcurrency} class.
     */
    private static int calculateDegreeOfConcurrency( String threadConfiguration )
    {
        if ( threadConfiguration == null )
        {
            throw new IllegalArgumentException( "Thread configuration must not be null." );
        }
        if ( threadConfiguration.endsWith( "C" ) )
        {
            threadConfiguration = threadConfiguration.substring( 0, threadConfiguration.length() - 1 );

            try
            {
                float coreMultiplier = Float.parseFloat( threadConfiguration );

                if ( coreMultiplier <= 0.0f )
                {
                    throw new IllegalArgumentException( "Invalid threads core multiplier value: '" + threadConfiguration
                            + "C'. Value must be positive." );
                }

                int threads = (int) ( coreMultiplier * Runtime.getRuntime().availableProcessors() );
                return threads == 0 ? 1 : threads;
            }
            catch ( NumberFormatException e )
            {
                throw new IllegalArgumentException(
                        "Invalid threads value: '" + threadConfiguration + "C'. Value must be positive." );
            }
        }
        else
        {
            try
            {
                int threads = Integer.parseInt( threadConfiguration );

                if ( threads <= 0 )
                {
                    throw new IllegalArgumentException(
                            "Invalid threads value: '" + threadConfiguration + "'. Value must be positive." );
                }

                return threads;
            }
            catch ( NumberFormatException e )
            {
                throw new IllegalArgumentException(
                        "Invalid threads value: '" + threadConfiguration + "'. Supported are integer values." );
            }
        }
    }
}
