package org.eclipse.aether.internal.test.util;

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

    /**
     * Creates a new logger factory that writes to the specified print stream.
     */
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

        TestLogger( PrintStream out )
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
