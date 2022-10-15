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
import java.util.Collections;
import java.util.List;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferListener;

/**
 * A download of metadata from a remote repository. A repository connector processing this download has to use
 * {@link #setException(MetadataTransferException)} to report the results of the transfer.
 */
public final class MetadataDownload extends MetadataTransfer {

    private String checksumPolicy = "";

    private String context = "";

    private List<RemoteRepository> repositories = Collections.emptyList();

    /**
     * Creates a new uninitialized download.
     */
    public MetadataDownload() {
        // enables default constructor
    }

    /**
     * Creates a new download with the specified properties.
     *
     * @param metadata       The metadata to download, may be {@code null}.
     * @param context        The context in which this download is performed, may be {@code null}.
     * @param file           The local file to download the metadata to, may be {@code null}.
     * @param checksumPolicy The checksum policy, may be {@code null}.
     */
    public MetadataDownload(Metadata metadata, String context, File file, String checksumPolicy) {
        setMetadata(metadata);
        setFile(file);
        setChecksumPolicy(checksumPolicy);
        setRequestContext(context);
    }

    @Override
    public MetadataDownload setMetadata(Metadata metadata) {
        super.setMetadata(metadata);
        return this;
    }

    @Override
    public MetadataDownload setFile(File file) {
        super.setFile(file);
        return this;
    }

    /**
     * Gets the checksum policy for this transfer.
     *
     * @return The checksum policy, never {@code null}.
     */
    public String getChecksumPolicy() {
        return checksumPolicy;
    }

    /**
     * Sets the checksum policy for this transfer.
     *
     * @param checksumPolicy The checksum policy, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setChecksumPolicy(String checksumPolicy) {
        this.checksumPolicy = (checksumPolicy != null) ? checksumPolicy : "";
        return this;
    }

    /**
     * Gets the context of this transfer.
     *
     * @return The context id, never {@code null}.
     */
    public String getRequestContext() {
        return context;
    }

    /**
     * Sets the request context of this transfer.
     *
     * @param context The context id, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setRequestContext(String context) {
        this.context = (context != null) ? context : "";
        return this;
    }

    /**
     * Gets the remote repositories that are being aggregated by the physically contacted remote repository (i.e. a
     * repository manager).
     *
     * @return The remote repositories being aggregated, never {@code null}.
     */
    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    /**
     * Sets the remote repositories that are being aggregated by the physically contacted remote repository (i.e. a
     * repository manager).
     *
     * @param repositories The remote repositories being aggregated, may be {@code null}.
     * @return This transfer for chaining, never {@code null}.
     */
    public MetadataDownload setRepositories(List<RemoteRepository> repositories) {
        if (repositories == null) {
            this.repositories = Collections.emptyList();
        } else {
            this.repositories = repositories;
        }
        return this;
    }

    @Override
    public MetadataDownload setException(MetadataTransferException exception) {
        super.setException(exception);
        return this;
    }

    @Override
    public MetadataDownload setListener(TransferListener listener) {
        super.setListener(listener);
        return this;
    }

    @Override
    public MetadataDownload setTrace(RequestTrace trace) {
        super.setTrace(trace);
        return this;
    }

    @Override
    public String toString() {
        return getMetadata() + " - " + getFile();
    }
}
