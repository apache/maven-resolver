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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.slf4j.ILoggerFactory;

/**
 * A logger factory that delegates to Slf4J logging.
 */
public class Slf4jLoggerFactory
    implements LoggerFactory, Service
{

    private static final boolean AVAILABLE;

    static
    {
        boolean available;
        try
        {
            Class.forName( "org.slf4j.ILoggerFactory" );
            available = true;
        }
        catch ( Exception e )
        {
            available = false;
        }
        catch ( LinkageError e )
        {
            available = false;
        }
        AVAILABLE = available;
    }

    public static boolean isSlf4jAvailable()
    {
        return AVAILABLE;
    }

    private ILoggerFactory factory;

    @SuppressWarnings( "unused" )
    private Slf4jLoggerFactory()
    {
        // enables no-arg constructor for service locator support
    }

    public Slf4jLoggerFactory( ILoggerFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "logger factory not specified" );
        }
        this.factory = factory;
    }

    public void initService( ServiceLocator locator )
    {
        factory = locator.getService( ILoggerFactory.class );
        if ( factory == null )
        {
            factory = org.slf4j.LoggerFactory.getILoggerFactory();
        }
    }

    public Logger getLogger( String name )
    {
        return new Slf4jLogger( factory.getLogger( name ) );
    }

    private static final class Slf4jLogger
        implements Logger
    {

        private final org.slf4j.Logger logger;

        public Slf4jLogger( org.slf4j.Logger logger )
        {
            this.logger = logger;
        }

        public boolean isDebugEnabled()
        {
            return logger.isDebugEnabled();
        }

        public void debug( String msg )
        {
            logger.debug( msg );
        }

        public void debug( String msg, Throwable error )
        {
            logger.debug( msg, error );
        }

        public boolean isWarnEnabled()
        {
            return logger.isWarnEnabled();
        }

        public void warn( String msg )
        {
            logger.warn( msg );
        }

        public void warn( String msg, Throwable error )
        {
            logger.warn( msg, error );
        }

    }

}
