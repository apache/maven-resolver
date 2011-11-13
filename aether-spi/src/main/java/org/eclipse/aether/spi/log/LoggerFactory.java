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
 * A factory to create loggers.
 */
public interface LoggerFactory
{

    /**
     * Gets a logger for a class with the specified name.
     * 
     * @param name The name of the class requesting a logger, must not be {@code null}.
     * @return The requested logger, never {@code null}.
     */
    Logger getLogger( String name );

}
