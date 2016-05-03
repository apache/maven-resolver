package org.eclipse.aether.spi.connector;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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

    ArtifactTransfer()
    {
        // hide
    }

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
