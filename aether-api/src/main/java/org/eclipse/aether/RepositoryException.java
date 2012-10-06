/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

/**
 * The base class for exceptions thrown by the repository system. <em>Note:</em> Unless otherwise noted, instances of
 * this class and its subclasses will not persist fields carrying extended error information during serialization.
 */
public class RepositoryException
    extends Exception
{

    public RepositoryException( String message )
    {
        super( message );
    }

    public RepositoryException( String message, Throwable cause )
    {
        super( message, cause );
    }

    /**
     * @noreference This method is not intended to be used by clients.
     */
    protected static String getMessage( String prefix, Throwable cause )
    {
        String msg = "";
        if ( cause != null )
        {
            msg = cause.getMessage();
            if ( msg == null || msg.length() <= 0 )
            {
                msg = cause.getClass().getSimpleName();
            }
            msg = prefix + msg;
        }
        return msg;
    }

}
