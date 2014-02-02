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
package org.eclipse.aether.transfer;

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when metadata was not found in a particular repository.
 */
public class MetadataNotFoundException
    extends MetadataTransferException
{

    /**
     * Creates a new exception with the specified metadata and local repository.
     * 
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved local repository, may be {@code null}.
     */
    public MetadataNotFoundException( Metadata metadata, LocalRepository repository )
    {
        super( metadata, null, "Could not find metadata " + metadata + getString( " in ", repository ) );
    }

    private static String getString( String prefix, LocalRepository repository )
    {
        if ( repository == null )
        {
            return "";
        }
        else
        {
            return prefix + repository.getId() + " (" + repository.getBasedir() + ")";
        }
    }

    /**
     * Creates a new exception with the specified metadata and repository.
     * 
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     */
    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository )
    {
        super( metadata, repository, "Could not find metadata " + metadata + getString( " in ", repository ) );
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     * 
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository, String message )
    {
        super( metadata, repository, message );
    }

    /**
     * Creates a new exception with the specified metadata, repository and detail message.
     * 
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param fromCache {@code true} if the exception was played back from the error cache, {@code false} if the
     *            exception actually just occurred.
     */
    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository, String message, boolean fromCache )
    {
        super( metadata, repository, message, fromCache );
    }

    /**
     * Creates a new exception with the specified metadata, repository, detail message and cause.
     * 
     * @param metadata The missing metadata, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public MetadataNotFoundException( Metadata metadata, RemoteRepository repository, String message, Throwable cause )
    {
        super( metadata, repository, message, cause );
    }

}
