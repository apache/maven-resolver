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
import org.eclipse.aether.spi.log.Logger;

/**
 * A logger that delegates to Plexus logging.
 */
@Component( role = Logger.class )
public class PlexusLogger
    implements Logger
{

    @Requirement
    private org.codehaus.plexus.logging.Logger logger;

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
