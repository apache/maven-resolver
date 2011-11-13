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
package org.eclipse.aether.spi.log;

/**
 * A logger that disables any logging.
 */
public class NullLogger
    implements Logger
{

    public static final Logger INSTANCE = new NullLogger();

    /**
     * Gets a logger from the specified factory for the given class, falling back to {@link #INSTANCE} if the factory is
     * {@code null} or fails to provide a logger.
     * 
     * @param loggerFactory The logger factory from which to get the logger, may be {@code null}.
     * @param type The class for which to get the logger, must not be {@code null}.
     * @return The requested logger, never {@code null}.
     */
    public static Logger getIfNull( LoggerFactory loggerFactory, Class<?> type )
    {
        if ( loggerFactory == null )
        {
            return INSTANCE;
        }
        Logger logger = loggerFactory.getLogger( type.getName() );
        if ( logger == null )
        {
            return INSTANCE;
        }
        return logger;
    }

    public boolean isDebugEnabled()
    {
        return false;
    }

    public void debug( String msg )
    {
    }

    public void debug( String msg, Throwable error )
    {
    }

    public boolean isWarnEnabled()
    {
        return false;
    }

    public void warn( String msg )
    {
    }

    public void warn( String msg, Throwable error )
    {
    }

}
