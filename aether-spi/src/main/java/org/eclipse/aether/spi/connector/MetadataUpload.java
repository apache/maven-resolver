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

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.MetadataTransferException;

/**
 * An upload of metadata to a remote repository. A repository connector processing this upload has to use
 * {@link #setState(State)} and {@link #setException(MetadataTransferException)} to report the results of the transfer.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public class MetadataUpload
    extends MetadataTransfer
{

    /**
     * Creates a new uninitialized upload.
     */
    public MetadataUpload()
    {
        // enables default constructor
    }

    /**
     * Creates a new upload with the specified properties.
     * 
     * @param metadata The metadata to upload, may be {@code null}.
     * @param file The local file to upload the metadata from, may be {@code null}.
     */
    public MetadataUpload( Metadata metadata, File file )
    {
        setMetadata( metadata );
        setFile( file );
    }

    @Override
    public MetadataUpload setMetadata( Metadata metadata )
    {
        super.setMetadata( metadata );
        return this;
    }

    @Override
    public MetadataUpload setFile( File file )
    {
        super.setFile( file );
        return this;
    }

    @Override
    public MetadataUpload setException( MetadataTransferException exception )
    {
        super.setException( exception );
        return this;
    }

    @Override
    public MetadataUpload setTrace( RequestTrace trace )
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
