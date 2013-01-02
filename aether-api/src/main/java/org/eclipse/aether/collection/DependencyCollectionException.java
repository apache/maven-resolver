/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
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

    public DependencyCollectionException( CollectResult result )
    {
        super( "Failed to collect dependencies for " + getSource( result ), getCause( result ) );
        this.result = result;
    }

    public DependencyCollectionException( CollectResult result, String message )
    {
        super( message, getCause( result ) );
        this.result = result;
    }

    public DependencyCollectionException( CollectResult result, String message, Throwable cause )
    {
        super( message, cause );
        this.result = result;
    }

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
