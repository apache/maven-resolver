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
package org.eclipse.aether.transfer;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when an artifact was not found in a particular repository.
 */
public class ArtifactNotFoundException
    extends ArtifactTransferException
{

    public ArtifactNotFoundException( Artifact artifact, RemoteRepository repository )
    {
        super( artifact, repository, getMessage( artifact, repository ) );
    }

    private static String getMessage( Artifact artifact, RemoteRepository repository )
    {
        StringBuilder buffer = new StringBuilder( 256 );
        buffer.append( "Could not find artifact " ).append( artifact );
        buffer.append( getString( " in ", repository ) );
        if ( artifact != null )
        {
            String localPath = artifact.getProperty( ArtifactProperties.LOCAL_PATH, null );
            if ( localPath != null && repository == null )
            {
                buffer.append( " at specified path " ).append( localPath );
            }
            String downloadUrl = artifact.getProperty( ArtifactProperties.DOWNLOAD_URL, null );
            if ( downloadUrl != null )
            {
                buffer.append( ", try downloading from " ).append( downloadUrl );
            }
        }
        return buffer.toString();
    }

    public ArtifactNotFoundException( Artifact artifact, RemoteRepository repository, String message )
    {
        super( artifact, repository, message );
    }

    public ArtifactNotFoundException( Artifact artifact, RemoteRepository repository, String message, Throwable cause )
    {
        super( artifact, repository, message, cause );
    }

}
