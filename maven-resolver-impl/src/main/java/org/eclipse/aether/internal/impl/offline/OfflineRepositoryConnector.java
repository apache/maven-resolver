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
package org.eclipse.aether.internal.impl.offline;

import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.internal.impl.Utils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.RepositoryOfflineException;

import static java.util.Objects.requireNonNull;

/**
 * Offline connector, that prevents ANY remote access in case session is offline.
 *
 * @since TBD
 */
public final class OfflineRepositoryConnector implements RepositoryConnector {
    private final RepositorySystemSession session;
    private final RemoteRepository remoteRepository;
    private final OfflineController offlineController;
    private final RepositoryConnector delegate;

    public OfflineRepositoryConnector(
            RepositorySystemSession session,
            RemoteRepository remoteRepository,
            OfflineController offlineController,
            RepositoryConnector delegate) {
        this.session = requireNonNull(session);
        this.remoteRepository = requireNonNull(remoteRepository);
        this.offlineController = requireNonNull(offlineController);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads) {
        try {
            Utils.checkOffline(session, offlineController, remoteRepository);
        } catch (RepositoryOfflineException e) {
            if (artifactDownloads != null && !artifactDownloads.isEmpty()) {
                artifactDownloads.forEach(
                        d -> d.setException(new ArtifactTransferException(d.getArtifact(), remoteRepository, e)));
            }
            if (metadataDownloads != null && !metadataDownloads.isEmpty()) {
                metadataDownloads.forEach(
                        d -> d.setException(new MetadataTransferException(d.getMetadata(), remoteRepository, e)));
            }
            return;
        }
        delegate.get(artifactDownloads, metadataDownloads);
    }

    @Override
    public void put(
            Collection<? extends ArtifactUpload> artifactUploads,
            Collection<? extends MetadataUpload> metadataUploads) {
        try {
            Utils.checkOffline(session, offlineController, remoteRepository);
        } catch (RepositoryOfflineException e) {
            if (artifactUploads != null && !artifactUploads.isEmpty()) {
                artifactUploads.forEach(
                        d -> d.setException(new ArtifactTransferException(d.getArtifact(), remoteRepository, e)));
            }
            if (metadataUploads != null && !metadataUploads.isEmpty()) {
                metadataUploads.forEach(
                        d -> d.setException(new MetadataTransferException(d.getMetadata(), remoteRepository, e)));
            }
            return;
        }
        delegate.put(artifactUploads, metadataUploads);
    }

    @Override
    public String toString() {
        return OfflinePipelineRepositoryConnectorFactory.NAME + "(" + delegate.toString() + ")";
    }
}
