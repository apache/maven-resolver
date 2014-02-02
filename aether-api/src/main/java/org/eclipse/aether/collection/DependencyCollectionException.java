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
package org.eclipse.aether.collection;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of bad artifact descriptors, version ranges or other issues encountered during calculation of the
 * dependency graph.
 */
public class DependencyCollectionException
    extends RepositoryException
{

    private final transient CollectResult result;

    /**
     * Creates a new exception with the specified result.
     * 
     * @param result The collection result at the point the exception occurred, may be {@code null}.
     */
    public DependencyCollectionException( CollectResult result )
    {
        super( "Failed to collect dependencies for " + getSource( result ), getCause( result ) );
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result and detail message.
     * 
     * @param result The collection result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public DependencyCollectionException( CollectResult result, String message )
    {
        super( message, getCause( result ) );
        this.result = result;
    }

    /**
     * Creates a new exception with the specified result, detail message and cause.
     * 
     * @param result The collection result at the point the exception occurred, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DependencyCollectionException( CollectResult result, String message, Throwable cause )
    {
        super( message, cause );
        this.result = result;
    }

    /**
     * Gets the collection result at the point the exception occurred. Despite being incomplete, callers might want to
     * use this result to fail gracefully and continue their operation with whatever interim data has been gathered.
     * 
     * @return The collection result or {@code null} if unknown.
     */
    public CollectResult getResult()
    {
        return result;
    }

    private static String getSource( CollectResult result )
    {
        if ( result == null )
        {
            return "";
        }

        CollectRequest request = result.getRequest();
        if ( request.getRoot() != null )
        {
            return request.getRoot().toString();
        }
        if ( request.getRootArtifact() != null )
        {
            return request.getRootArtifact().toString();
        }

        return request.getDependencies().toString();
    }

    private static Throwable getCause( CollectResult result )
    {
        Throwable cause = null;
        if ( result != null && !result.getExceptions().isEmpty() )
        {
            cause = result.getExceptions().get( 0 );
        }
        return cause;
    }

}
