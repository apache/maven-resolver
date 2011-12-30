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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.transfer.ArtifactTransferException;

/**
 * A download/upload of an artifact.
 * 
 * @noextend This class is not intended to be extended by clients.
 */
public abstract class ArtifactTransfer
    extends Transfer
{

    private Artifact artifact;

    private File file;

    private ArtifactTransferException exception;

    /**
     * Gets the artifact being transferred.
     * 
     * @return The artifact being transferred or {@code null} if not set.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact to transfer.
     * 
     * @param artifact The artifact, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public ArtifactTransfer setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the local file the artifact is downloaded to or uploaded from. In case of a download, a connector should
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
     * Sets the local file the artifact is downloaded to or uploaded from.
     * 
     * @param file The local file, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public ArtifactTransfer setFile( File file )
    {
        this.file = file;
        return this;
    }

    /**
     * Gets the exception that occurred during the transfer (if any).
     * 
     * @return The exception or {@code null} if the transfer was successful.
     */
    public ArtifactTransferException getException()
    {
        return exception;
    }

    /**
     * Sets the exception that occurred during the transfer.
     * 
     * @param exception The exception, may be {@code null} to denote a successful transfer.
     * @return This transfer for chaining, never {@code null}.
     */
    public ArtifactTransfer setException( ArtifactTransferException exception )
    {
        this.exception = exception;
        return this;
    }

}
