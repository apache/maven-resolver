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
package org.eclipse.aether.spi.connector;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.MetadataTransferException;

/**
 * A download of metadata from a remote repository. A repository connector processing this download has to use
 * {@link #setState(State)} and {@link #setException(MetadataTransferException)} to report the results of the transfer.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public class MetadataDownload
    extends MetadataTransfer
{

    private String checksumPolicy = "";

    private String context = "";

    private List<RemoteRepository> repositories = Collections.emptyList();

    /**
     * Creates a new uninitialized download.
     */
    public MetadataDownload()
    {
        // enables default constructor
    }

    /**
     * Creates a new download with the specified properties.
     * 
     * @param metadata The metadata to download, may be {@code null}.
     * @param context The context in which this download is performed, may be {@code null}.
     * @param file The local file to download the metadata to, may be {@code null}.
     * @param checksumPolicy The checksum policy, may be {@code null}.
     */
    public MetadataDownload( Metadata metadata, String context, File file, String checksumPolicy )
    {
        setMetadata( metadata );
        setFile( file );
        setChecksumPolicy( checksumPolicy );
        setRequestContext( context );
    }

    @Override
    public MetadataDownload setMetadata( Metadata metadata )
    {
        super.setMetadata( metadata );
        return this;
    }

    @Override
    public MetadataDownload setFile( File file )
    {
        super.setFile( file );
        return this;
    }

    /**
     * Gets the checksum policy for this transfer.
     * 
     * @return The checksum policy, never {@code null}.
     */
    public String getChecksumPolicy()
    {
        return checksumPolicy;
    }

    /**
     * Sets the checksum policy for this transfer.
     * 
     * @param checksumPolicy The checksum policy, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setChecksumPolicy( String checksumPolicy )
    {
        this.checksumPolicy = ( checksumPolicy != null ) ? checksumPolicy : "";
        return this;
    }

    /**
     * Gets the context of this transfer.
     * 
     * @return The context id, never {@code null}.
     */
    public String getRequestContext()
    {
        return context;
    }

    /**
     * Sets the request context of this transfer.
     * 
     * @param context The context id, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setRequestContext( String context )
    {
        this.context = ( context != null ) ? context : "";
        return this;
    }

    /**
     * Gets the remote repositories that are being aggregated by the physically contacted remote repository (i.e. a
     * repository manager).
     * 
     * @return The remote repositories being aggregated, never {@code null}.
     */
    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the remote repositories that are being aggregated by the physically contacted remote repository (i.e. a
     * repository manager).
     * 
     * @param repositories The remote repositories being aggregated, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setRepositories( List<RemoteRepository> repositories )
    {
        if ( repositories == null )
        {
            this.repositories = Collections.emptyList();
        }
        else
        {
            this.repositories = repositories;
        }
        return this;
    }

    @Override
    public MetadataDownload setException( MetadataTransferException exception )
    {
        super.setException( exception );
        return this;
    }

    @Override
    public MetadataDownload setTrace( RequestTrace trace )
    {
        super.setTrace( trace );
        return this;
    }

    @Override
    public String toString()
    {
        return getState() + " " + getMetadata() + " - " + getFile();
    }

}
