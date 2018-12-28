package org.eclipse.aether.internal.impl.slf4j;

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

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.sisu.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

/**
 * A logger factory that delegates to <a href="http://www.slf4j.org/" target="_blank">SLF4J</a> logging.
 */
@Named( "slf4j" )
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
        catch ( Exception | LinkageError e )
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

    /**
     * Creates an instance of this logger factory.
     */
    public Slf4jLoggerFactory()
    {
        // enables no-arg constructor
    }

    @Inject
    Slf4jLoggerFactory( @Nullable ILoggerFactory factory )
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

        Slf4jLogger( org.slf4j.Logger logger )
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

        Slf4jLoggerEx( LocationAwareLogger logger )
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
