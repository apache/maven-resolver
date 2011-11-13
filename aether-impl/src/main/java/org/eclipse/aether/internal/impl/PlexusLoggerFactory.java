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

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.LoggerManager;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;

/**
 * A logger factory that delegates to Plexus logging.
 */
@Component( role = LoggerFactory.class )
public class PlexusLoggerFactory
    implements LoggerFactory
{

    @Requirement
    private LoggerManager loggerManager;

    public Logger getLogger( String name )
    {
        return new PlexusLogger( loggerManager.getLoggerForComponent( name ) );
    }

    private static final class PlexusLogger
        implements Logger
    {

        private final org.codehaus.plexus.logging.Logger logger;

        public PlexusLogger( org.codehaus.plexus.logging.Logger logger )
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
