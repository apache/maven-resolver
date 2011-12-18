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
package org.eclipse.aether.resolution;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a unresolvable dependencies.
 */
public class DependencyResolutionException
    extends RepositoryException
{

    private final DependencyResult result;

    public DependencyResolutionException( DependencyResult result, Throwable cause )
    {
        super( getMessage( cause ), cause );
        this.result = result;
    }

    public DependencyResolutionException( DependencyResult result, String message, Throwable cause )
    {
        super( message, cause );
        this.result = result;
    }

    private static String getMessage( Throwable cause )
    {
        String msg = null;
        if ( cause != null )
        {
            msg = cause.getMessage();
        }
        if ( msg == null || msg.length() <= 0 )
        {
            msg = "Could not resolve transitive dependencies";
        }
        return msg;
    }

    public DependencyResult getResult()
    {
        return result;
    }

}
