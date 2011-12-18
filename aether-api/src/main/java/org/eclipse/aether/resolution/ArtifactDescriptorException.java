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
 * Thrown in case of an unreadable or unresolvable artifact descriptor.
 */
public class ArtifactDescriptorException
    extends RepositoryException
{

    private final ArtifactDescriptorResult result;

    public ArtifactDescriptorException( ArtifactDescriptorResult result )
    {
        super( "Failed to read artifact descriptor"
            + ( result != null ? " for " + result.getRequest().getArtifact() : "" ), getCause( result ) );
        this.result = result;
    }

    public ArtifactDescriptorException( ArtifactDescriptorResult result, String message )
    {
        super( message, getCause( result ) );
        this.result = result;
    }

    public ArtifactDescriptorException( ArtifactDescriptorResult result, String message, Throwable cause )
    {
        super( message, cause );
        this.result = result;
    }

    public ArtifactDescriptorResult getResult()
    {
        return result;
    }

    private static Throwable getCause( ArtifactDescriptorResult result )
    {
        Throwable cause = null;
        if ( result != null && !result.getExceptions().isEmpty() )
        {
            cause = result.getExceptions().get( 0 );
        }
        return cause;
    }

}
