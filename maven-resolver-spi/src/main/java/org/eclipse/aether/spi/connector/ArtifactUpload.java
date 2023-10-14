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

/**
 * An upload of an artifact to a remote repository. A repository connector processing this upload has to use
 * {@link #setException(ArtifactTransferException)} to report the results of the transfer.
 */
public final class ArtifactUpload extends ArtifactTransfer {
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
     * @param file The local file to upload the artifact from, may be {@code null}.
     */
    public ArtifactUpload(Artifact artifact, File file) {
        setArtifact(artifact);
        setFile(file);
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

    @Override
    public String toString() {
        return getArtifact() + " - " + getFile();
    }
}
