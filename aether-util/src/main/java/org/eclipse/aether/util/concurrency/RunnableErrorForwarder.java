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
package org.eclipse.aether.util.concurrency;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

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
public final class RunnableErrorForwarder
{

    private final Thread thread = Thread.currentThread();

    private final AtomicInteger counter = new AtomicInteger();

    private final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

    /**
     * Wraps the specified runnable into an equivalent runnable that will allow forwarding of uncaught errors.
     * 
     * @param runnable The runnable from which to forward errors, must not be {@code null}.
     * @return The error-forwarding runnable to eventually execute, never {@code null}.
     */
    public Runnable wrap( final Runnable runnable )
    {
        if ( runnable == null )
        {
            throw new IllegalArgumentException( "runnable missing" );
        }

        counter.incrementAndGet();

        return new Runnable()
        {
            public void run()
            {
                try
                {
                    runnable.run();
                }
                catch ( RuntimeException e )
                {
                    error.compareAndSet( null, e );
                    throw e;
                }
                catch ( Error e )
                {
                    error.compareAndSet( null, e );
                    throw e;
                }
                finally
                {
                    counter.decrementAndGet();
                    LockSupport.unpark( thread );
                }
            }
        };
    }

    /**
     * Causes the current thread to wait until all previously {@link #wrap(Runnable) wrapped} runnables have terminated
     * and potentially re-throws an uncaught {@link RuntimeException} or {@link Error} from any of the runnables. In
     * case multiple runnables encountered uncaught errors, one error is arbitrarily selected.
     */
    public void await()
    {
        awaitTerminationOfAllRunnables();

        Throwable error = this.error.get();
        if ( error != null )
        {
            if ( error instanceof RuntimeException )
            {
                throw (RuntimeException) error;
            }
            else if ( error instanceof ThreadDeath )
            {
                throw new IllegalStateException( error );
            }
            else if ( error instanceof Error )
            {
                throw (Error) error;
            }
            throw new IllegalStateException( error );
        }
    }

    private void awaitTerminationOfAllRunnables()
    {
        boolean interrupted = false;

        while ( counter.get() > 0 )
        {
            LockSupport.park();

            if ( Thread.interrupted() )
            {
                interrupted = true;
            }
        }

        if ( interrupted )
        {
            Thread.currentThread().interrupt();
        }
    }

}
