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
 * Thrown in case of an unparseable or unresolvable version range.
 */
public class VersionRangeResolutionException
    extends RepositoryException
{

    private final transient VersionRangeResult result;

    /**
     * Creates a new exception with the specified result.
     * 
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     */
    public VersionRangeResolutionException( VersionRangeResult result )
    {
        super( getMessage( result ), getCause( result ) );
        this.result = result;
    }

    private static String getMessage( VersionRangeResult result )
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "Failed to resolve version range" );
        if ( result != null )
        {
            buffer.append( " for " ).append( result.getRequest().getArtifact() );
            if ( !result.getExceptions().isEmpty() )
            {
                buffer.append( ": " ).append( result.getExceptions().iterator().next().getMessage() );
            }
        }
        return buffer.toString();
    }

    private static Throwable getCause( VersionRangeResult result )
    {
        Throwable cause = null;
        if ( result != null && !result.getExceptions().isEmpty() )
        {
            cause = result.getExceptions().get( 0 );
        }
        return cause;
    }

    /**
     * Creates a new exception with the specified result and detail message.
     * 
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public VersionRangeResolutionException( VersionRangeResult result, String message )
    {
        super( message );
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     * 
     * @param result The version range result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public VersionRangeResolutionException( VersionRangeResult result, String message, Throwable cause )
    {
        super( message, cause );
        this.result = result;
    }

    /**
     * Gets the version range result at the point the exception occurred. Despite being incomplete, callers might want
     * to use this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     * 
     * @return The version range result or {@code null} if unknown.
     */
    public VersionRangeResult getResult()
    {
        return result;
    }

}
