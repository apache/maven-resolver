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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when metadata could not be uploaded/downloaded to/from a particular remote repository.
 */
public class MetadataTransferException
    extends RepositoryException
{

    private final transient Metadata metadata;

    private final transient RemoteRepository repository;

    private final boolean fromCache;

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

    public MetadataTransferException( Metadata metadata, RemoteRepository repository, String message )
    {
        this( metadata, repository, message, false );
    }

    public MetadataTransferException( Metadata metadata, RemoteRepository repository, String message, boolean fromCache )
    {
        super( message );

        this.metadata = metadata;
        this.repository = repository;
        this.fromCache = fromCache;
    }

    public MetadataTransferException( Metadata metadata, RemoteRepository repository, Throwable cause )
    {
        this( metadata, repository, "Could not transfer metadata " + metadata + getString( " from/to ", repository )
            + getMessage( ": ", cause ), cause );
    }

    public MetadataTransferException( Metadata metadata, RemoteRepository repository, String message, Throwable cause )
    {
        super( message, cause );

        this.metadata = metadata;
        this.repository = repository;
        this.fromCache = false;
    }

    public Metadata getMetadata()
    {
        return metadata;
    }

    public RemoteRepository getRepository()
    {
        return repository;
    }

    public boolean isFromCache()
    {
        return fromCache;
    }

}
