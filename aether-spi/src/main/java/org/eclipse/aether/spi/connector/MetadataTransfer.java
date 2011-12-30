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

import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.MetadataTransferException;

/**
 * A download/upload of metadata.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class MetadataTransfer
    extends Transfer
{

    private Metadata metadata;

    private File file;

    private MetadataTransferException exception;

    /**
     * Gets the metadata being transferred.
     * 
     * @return The metadata being transferred or {@code null} if not set.
     */
    public Metadata getMetadata()
    {
        return metadata;
    }

    /**
     * Sets the metadata to transfer.
     * 
     * @param metadata The metadata, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataTransfer setMetadata( Metadata metadata )
    {
        this.metadata = metadata;
        return this;
    }

    /**
     * Gets the local file the metadata is downloaded to or uploaded from. In case of a download, a connector should
     * first transfer the bytes to a temporary file and only overwrite the target file once the entire download is
     * completed such that an interrupted/failed download does not corrupt the current file contents.
     * 
     * @return The local file or {@code null} if not set.
     */
    public File getFile()
    {
        return file;
    }

    /**
     * Sets the local file the metadata is downloaded to or uploaded from.
     * 
     * @param file The local file, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataTransfer setFile( File file )
    {
        this.file = file;
        return this;
    }

    /**
     * Gets the exception that occurred during the transfer (if any).
     * 
     * @return The exception or {@code null} if the transfer was successful.
     */
    public MetadataTransferException getException()
    {
        return exception;
    }

    /**
     * Sets the exception that occurred during the transfer.
     * 
     * @param exception The exception, may be {@code null} to denote a successful transfer.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataTransfer setException( MetadataTransferException exception )
    {
        this.exception = exception;
        return this;
    }

}
