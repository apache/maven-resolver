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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when an artifact could not be uploaded/downloaded to/from a particular remote repository.
 */
public class ArtifactTransferException
    extends RepositoryException
{

    private final Artifact artifact;

    private final RemoteRepository repository;

    static String getString( String prefix, RemoteRepository repository )
    {
        if ( repository == null )
        {
            return "";
        }
        else
        {
            return prefix + repository.getId() + " (" + repository.getUrl() + ")";
        }
    }

    public ArtifactTransferException( Artifact artifact, RemoteRepository repository, String message )
    {
        super( message );

        this.artifact = artifact;
        this.repository = repository;
    }

    public ArtifactTransferException( Artifact artifact, RemoteRepository repository, Throwable cause )
    {
        super( "Could not transfer artifact " + artifact + getString( " from/to ", repository )
            + getMessage( ": ", cause ), cause );

        this.artifact = artifact;
        this.repository = repository;
    }

    public ArtifactTransferException( Artifact artifact, RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.artifact = artifact;
        this.repository = repository;
    }

    public Artifact getArtifact()
    {
        return artifact;
    }

    public RemoteRepository getRepository()
    {
        return repository;
    }

}
