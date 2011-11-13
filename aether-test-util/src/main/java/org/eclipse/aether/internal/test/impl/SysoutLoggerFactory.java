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
package org.eclipse.aether.internal.test.impl;

import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;

public final class SysoutLoggerFactory
    implements LoggerFactory
{

    public static final Logger LOGGER = new SysoutLogger();

    public Logger getLogger( String name )
    {
        return LOGGER;
    }

    private static final class SysoutLogger
        implements Logger
    {

        public void warn( String msg, Throwable error )
        {
            warn( msg );
            if ( error != null )
            {
                error.printStackTrace( System.err );
            }
        }

        public void warn( String msg )
        {
            System.err.println( msg );
        }

        public boolean isWarnEnabled()
        {
            return true;
        }

        public boolean isDebugEnabled()
        {
            return true;
        }

        public void debug( String msg, Throwable error )
        {
            debug( msg );
            if ( error != null )
            {
                error.printStackTrace( System.err );
            }
        }

        public void debug( String msg )
        {
            System.out.println( msg );
        }

    }

}
