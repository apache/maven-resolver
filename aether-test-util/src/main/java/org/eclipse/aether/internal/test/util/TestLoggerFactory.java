/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.io.PrintStream;

import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;

/**
 * A logger factory that writes to some {@link PrintStream}.
 */
public final class TestLoggerFactory
    implements LoggerFactory
{

    private final Logger logger;

    /**
     * Creates a new logger factory that writes to {@link System#out}.
     */
    public TestLoggerFactory()
    {
        this( null );
    }

    public TestLoggerFactory( PrintStream out )
    {
        logger = new TestLogger( out );
    }

    public Logger getLogger( String name )
    {
        return logger;
    }

    private static final class TestLogger
        implements Logger
    {

        private final PrintStream out;

        public TestLogger( PrintStream out )
        {
            this.out = ( out != null ) ? out : System.out;
        }

        public boolean isWarnEnabled()
        {
            return true;
        }

        public void warn( String msg, Throwable error )
        {
            out.println( "[WARN] " + msg );
            if ( error != null )
            {
                error.printStackTrace( out );
            }
        }

        public void warn( String msg )
        {
            warn( msg, null );
        }

        public boolean isDebugEnabled()
        {
            return true;
        }

        public void debug( String msg, Throwable error )
        {
            out.println( "[DEBUG] " + msg );
            if ( error != null )
            {
                error.printStackTrace( out );
            }
        }

        public void debug( String msg )
        {
            debug( msg, null );
        }

    }

}
