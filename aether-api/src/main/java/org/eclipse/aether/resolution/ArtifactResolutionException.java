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
package org.eclipse.aether.resolution;

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.RepositoryOfflineException;

/**
 * Thrown in case of a unresolvable artifacts.
 */
public class ArtifactResolutionException
    extends RepositoryException
{

    private final transient List<ArtifactResult> results;

    public ArtifactResolutionException( List<ArtifactResult> results )
    {
        super( getMessage( results ), getCause( results ) );
        this.results = ( results != null ) ? results : Collections.<ArtifactResult> emptyList();
    }

    public ArtifactResolutionException( List<ArtifactResult> results, String message )
    {
        super( message, getCause( results ) );
        this.results = ( results != null ) ? results : Collections.<ArtifactResult> emptyList();
    }

    public ArtifactResolutionException( List<ArtifactResult> results, String message, Throwable cause )
    {
        super( message, cause );
        this.results = ( results != null ) ? results : Collections.<ArtifactResult> emptyList();
    }

    public List<ArtifactResult> getResults()
    {
        return results;
    }

    public ArtifactResult getResult()
    {
        return ( results != null && !results.isEmpty() ) ? results.get( 0 ) : null;
    }

    private static String getMessage( List<? extends ArtifactResult> results )
    {
        StringBuilder buffer = new StringBuilder( 256 );

        buffer.append( "The following artifacts could not be resolved: " );

        int unresolved = 0;

        String sep = "";
        for ( ArtifactResult result : results )
        {
            if ( !result.isResolved() )
            {
                unresolved++;

                buffer.append( sep );
                buffer.append( result.getRequest().getArtifact() );
                sep = ", ";
            }
        }

        Throwable cause = getCause( results );
        if ( cause != null )
        {
            if ( unresolved == 1 )
            {
                buffer.setLength( 0 );
                buffer.append( cause.getMessage() );
            }
            else
            {
                buffer.append( ": " ).append( cause.getMessage() );
            }
        }

        return buffer.toString();
    }

    private static Throwable getCause( List<? extends ArtifactResult> results )
    {
        for ( ArtifactResult result : results )
        {
            if ( !result.isResolved() )
            {
                Throwable notFound = null, offline = null;
                for ( Throwable t : result.getExceptions() )
                {
                    if ( t instanceof ArtifactNotFoundException )
                    {
                        if ( notFound == null )
                        {
                            notFound = t;
                        }
                        if ( offline == null && t.getCause() instanceof RepositoryOfflineException )
                        {
                            offline = t;
                        }
                    }
                    else
                    {
                        return t;
                    }

                }
                if ( offline != null )
                {
                    return offline;
                }
                if ( notFound != null )
                {
                    return notFound;
                }
            }
        }
        return null;
    }

}
