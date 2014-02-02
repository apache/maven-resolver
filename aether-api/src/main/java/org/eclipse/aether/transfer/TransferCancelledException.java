/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case an upload/download was cancelled (e.g. due to user request).
 */
public class TransferCancelledException
    extends RepositoryException
{

    /**
     * Creates a new exception with a stock detail message.
     */
    public TransferCancelledException()
    {
        super( "The operation was cancelled." );
    }

    /**
     * Creates a new exception with the specified detail message.
     * 
     * @param message The detail message, may be {@code null}.
     */
    public TransferCancelledException( String message )
    {
        super( message );
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     * 
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public TransferCancelledException( String message, Throwable cause )
    {
        super( message, cause );
    }

}
