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
package org.eclipse.aether.resolution;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a unresolvable dependencies.
 */
public class DependencyResolutionException
    extends RepositoryException
{

    private final transient DependencyResult result;

    /**
     * Creates a new exception with the specified result and cause.
     * 
     * @param result The dependency result at the point the exception occurred, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DependencyResolutionException( DependencyResult result, Throwable cause )
    {
        super( getMessage( cause ), cause );
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     * 
     * @param result The dependency result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
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

    /**
     * Gets the dependency result at the point the exception occurred. Despite being incomplete, callers might want to
     * use this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     * 
     * @return The dependency result or {@code null} if unknown.
     */
    public DependencyResult getResult()
    {
        return result;
    }

}
