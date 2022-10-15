/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.spi.connector;

import java.io.File;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transform.FileTransformer;

/**
 * An upload of an artifact to a remote repository. A repository connector processing this upload has to use
 * {@link #setException(ArtifactTransferException)} to report the results of the transfer.
 */
public final class ArtifactUpload extends ArtifactTransfer {
    private FileTransformer fileTransformer;

    /**
     * Creates a new uninitialized upload.
     */
    public ArtifactUpload() {
        // enables default constructor
    }

    /**
     * Creates a new upload with the specified properties.
     *
     * @param artifact The artifact to upload, may be {@code null}.
     * @param file     The local file to upload the artifact from, may be {@code null}.
     */
    public ArtifactUpload(Artifact artifact, File file) {
        setArtifact(artifact);
        setFile(file);
    }

    /**
     * <p>
     * Creates a new upload with the specified properties.
     * </p>
     * <p>
     * <strong>IMPORTANT</strong> When using a fileTransformer, the content of the file is stored in memory to ensure
     * that file content and checksums stay in sync!
     * </p>
     *
     * @param artifact        The artifact to upload, may be {@code null}.
     * @param file            The local file to upload the artifact from, may be {@code null}.
     * @param fileTransformer The file transformer, may be {@code null}.
     */
    public ArtifactUpload(Artifact artifact, File file, FileTransformer fileTransformer) {
        setArtifact(artifact);
        setFile(file);
        setFileTransformer(fileTransformer);
    }

    @Override
    public ArtifactUpload setArtifact(Artifact artifact) {
        super.setArtifact(artifact);
        return this;
    }

    @Override
    public ArtifactUpload setFile(File file) {
        super.setFile(file);
        return this;
    }

    @Override
    public ArtifactUpload setException(ArtifactTransferException exception) {
        super.setException(exception);
        return this;
    }

    @Override
    public ArtifactUpload setListener(TransferListener listener) {
        super.setListener(listener);
        return this;
    }

    @Override
    public ArtifactUpload setTrace(RequestTrace trace) {
        super.setTrace(trace);
        return this;
    }

    public ArtifactUpload setFileTransformer(FileTransformer fileTransformer) {
        this.fileTransformer = fileTransformer;
        return this;
    }

    public FileTransformer getFileTransformer() {
        return fileTransformer;
    }

    @Override
    public String toString() {
        if (getFileTransformer() != null) {
            return getArtifact() + " >>> " + getFileTransformer().transformArtifact(getArtifact()) + " - " + getFile();
        } else {
            return getArtifact() + " - " + getFile();
        }
    }
}
