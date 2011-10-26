/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.file;

import static org.eclipse.aether.connector.file.FileRepositoryConnectorFactory.*;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.util.ConfigUtils;

/**
 * Provides methods to configure the used {@link ThreadPoolExecutor}.
 */
abstract class ParallelRepositoryConnector
{

    /*
     * Default Configuration
     */
    private static final int MAX_POOL_SIZE = 5;

    private boolean closed = false;

    /**
     * The executor to use.
     * 
     * @see #initExecutor()
     */
    protected Executor executor;

    protected void initExecutor( Map<String, Object> config )
    {
        if ( executor == null )
        {
            int threads = ConfigUtils.getInteger( config, MAX_POOL_SIZE, CFG_PREFIX + ".threads" );

            if ( threads <= 1 )
            {
                executor = new Executor()
                {
                    public void execute( Runnable command )
                    {
                        command.run();
                    }
                };
            }
            else
            {
                ThreadFactory threadFactory = new RepositoryConnectorThreadFactory( getClass().getSimpleName() );

                executor =
                    new ThreadPoolExecutor( threads, threads, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                                            threadFactory );
            }
        }
    }

    public void close()
    {
        this.closed = true;

        if ( executor instanceof ExecutorService )
        {
            ( (ExecutorService) executor ).shutdown();
        }
    }

    protected void checkClosed()
    {
        if ( closed )
        {
            throw new IllegalStateException( "Connector is closed" );
        }
    }

    protected static class RepositoryConnectorThreadFactory
        implements ThreadFactory
    {

        private final AtomicInteger counter = new AtomicInteger( 1 );

        private final String threadName;

        public RepositoryConnectorThreadFactory( String threadName )
        {
            this.threadName = threadName;
        }

        public Thread newThread( Runnable r )
        {
            Thread t = new Thread( r, threadName + "-" + counter.getAndIncrement() );
            t.setDaemon( true );
            return t;
        }

    }

}
