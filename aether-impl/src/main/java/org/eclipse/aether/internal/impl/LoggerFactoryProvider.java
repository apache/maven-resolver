/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
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
import javax.inject.Provider;
import javax.inject.Singleton;

import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;

/**
 * Helps Sisu-based applications to pick the right logger factory depending on the classpath.
 */
@Named
@Singleton
public class LoggerFactoryProvider
    implements Provider<LoggerFactory>
{

    @Inject
    @Named( "slf4j" )
    private Provider<LoggerFactory> slf4j;

    public LoggerFactory get()
    {
        try
        {
            LoggerFactory factory = slf4j.get();
            if ( factory != null )
            {
                return factory;
            }
        }
        catch ( LinkageError e )
        {
            // fall through
        }
        catch ( RuntimeException e )
        {
            // fall through
        }
        return NullLoggerFactory.INSTANCE;
    }

}
