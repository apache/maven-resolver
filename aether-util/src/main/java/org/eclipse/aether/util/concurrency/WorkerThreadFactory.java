/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory to create worker threads with a given name prefix.
 */
public final class WorkerThreadFactory
    implements ThreadFactory
{

    private final ThreadFactory factory;

    private final String namePrefix;

    private final AtomicInteger threadIndex;

    private static final AtomicInteger poolIndex = new AtomicInteger();

    /**
     * Creates a new thread factory whose threads will have names using the specified prefix.
     * 
     * @param namePrefix The prefix for the thread names, may be {@code null} or empty to derive the prefix from the
     *            caller's simple class name.
     */
    public WorkerThreadFactory( String namePrefix )
    {
        this.factory = Executors.defaultThreadFactory();
        this.namePrefix =
            ( ( namePrefix != null && namePrefix.length() > 0 ) ? namePrefix : getCallerSimpleClassName() + '-' )
                + poolIndex.getAndIncrement() + '-';
        threadIndex = new AtomicInteger();
    }

    private static String getCallerSimpleClassName()
    {
        StackTraceElement[] stack = new Exception().getStackTrace();
        if ( stack == null || stack.length <= 2 )
        {
            return "Worker-";
        }
        String name = stack[2].getClassName();
        name = name.substring( name.lastIndexOf( '.' ) + 1 );
        return name;
    }

    public Thread newThread( Runnable r )
    {
        Thread thread = factory.newThread( r );
        thread.setName( namePrefix + threadIndex.getAndIncrement() );
        thread.setDaemon( true );
        return thread;
    }

}
