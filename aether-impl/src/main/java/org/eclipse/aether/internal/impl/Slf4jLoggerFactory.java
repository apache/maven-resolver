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
package org.eclipse.aether.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

/**
 * A logger factory that delegates to Slf4J logging.
 */
@Named
public class Slf4jLoggerFactory
    implements LoggerFactory, Service
{

    private static final boolean AVAILABLE;

    static
    {
        boolean available;
        try
        {
            Slf4jLoggerFactory.class.getClassLoader().loadClass( "org.slf4j.ILoggerFactory" );
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

    public Slf4jLoggerFactory()
    {
        // enables no-arg constructor
    }

    @Inject
    Slf4jLoggerFactory( ILoggerFactory factory )
    {
        setLoggerFactory( factory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( ILoggerFactory.class ) );
    }

    public Slf4jLoggerFactory setLoggerFactory( ILoggerFactory factory )
    {
        this.factory = factory;
        return this;
    }

    public Logger getLogger( String name )
    {
        org.slf4j.Logger logger = getFactory().getLogger( name );
        if ( logger instanceof LocationAwareLogger )
        {
            return new Slf4jLoggerEx( (LocationAwareLogger) logger );
        }
        return new Slf4jLogger( logger );
    }

    private ILoggerFactory getFactory()
    {
        if ( factory == null )
        {
            factory = org.slf4j.LoggerFactory.getILoggerFactory();
        }
        return factory;
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

    private static final class Slf4jLoggerEx
        implements Logger
    {

        private static final String FQCN = Slf4jLoggerEx.class.getName();

        private final LocationAwareLogger logger;

        public Slf4jLoggerEx( LocationAwareLogger logger )
        {
            this.logger = logger;
        }

        public boolean isDebugEnabled()
        {
            return logger.isDebugEnabled();
        }

        public void debug( String msg )
        {
            logger.log( null, FQCN, LocationAwareLogger.DEBUG_INT, msg, null, null );
        }

        public void debug( String msg, Throwable error )
        {
            logger.log( null, FQCN, LocationAwareLogger.DEBUG_INT, msg, null, error );
        }

        public boolean isWarnEnabled()
        {
            return logger.isWarnEnabled();
        }

        public void warn( String msg )
        {
            logger.log( null, FQCN, LocationAwareLogger.WARN_INT, msg, null, null );
        }

        public void warn( String msg, Throwable error )
        {
            logger.log( null, FQCN, LocationAwareLogger.WARN_INT, msg, null, error );
        }

    }

}
