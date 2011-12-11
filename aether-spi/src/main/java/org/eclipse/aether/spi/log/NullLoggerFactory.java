/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
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
 * A logger factory that disables any logging.
 */
public final class NullLoggerFactory
    implements LoggerFactory
{

    /**
     * The singleton instance of this factory.
     */
    public static final LoggerFactory INSTANCE = new NullLoggerFactory();

    /**
     * The singleton logger used by this factory.
     */
    public static final Logger LOGGER = new NullLogger();

    public Logger getLogger( String name )
    {
        return LOGGER;
    }

    private NullLoggerFactory()
    {
        // hide constructor
    }

    /**
     * Gets a logger from the specified factory for the given class, falling back to a logger from this factory if the
     * specified factory is {@code null} or fails to provide a logger.
     * 
     * @param loggerFactory The logger factory from which to get the logger, may be {@code null}.
     * @param type The class for which to get the logger, must not be {@code null}.
     * @return The requested logger, never {@code null}.
     */
    public static Logger getSafeLogger( LoggerFactory loggerFactory, Class<?> type )
    {
        if ( loggerFactory == null )
        {
            return LOGGER;
        }
        Logger logger = loggerFactory.getLogger( type.getName() );
        if ( logger == null )
        {
            return LOGGER;
        }
        return logger;
    }

}
