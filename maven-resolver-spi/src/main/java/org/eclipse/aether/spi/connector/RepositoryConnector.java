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

import java.io.Closeable;
import java.util.Collection;

/**
 * A connector for a remote repository. The connector is responsible for downloading/uploading of artifacts and metadata
 * from/to a remote repository.
 * <p>
 * If applicable, a connector should obey connect/request timeouts and other relevant settings from the
 * {@link org.eclipse.aether.RepositorySystemSession#getConfigProperties() configuration properties} of the repository
 * session it has been obtained for. However, a connector must not emit any events to the transfer listener configured
 * for the session. Instead, transfer events must be emitted only to the listener (if any) specified for a given
 * download/upload request.
 * <p>
 * <strong>Note:</strong> While a connector itself can use multiple threads internally to performs the transfers,
 * clients must not call a connector concurrently, i.e. connectors are generally not thread-safe.
 *
 * @see org.eclipse.aether.spi.connector.transport.TransporterProvider
 * @see org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider
 * @see org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider
 */
public interface RepositoryConnector extends Closeable {

    /**
     * Performs the specified downloads. If a download fails, the connector stores the underlying exception in the
     * download object such that callers can inspect the result via {@link ArtifactDownload#getException()} and
     * {@link MetadataDownload#getException()}, respectively. If reasonable, a connector should continue to process the
     * remaining downloads after an error to retrieve as many items as possible. The connector may perform the transfers
     * concurrently and in any order.
     *
     * @param artifactDownloads The artifact downloads to perform, may be {@code null} or empty.
     * @param metadataDownloads The metadata downloads to perform, may be {@code null} or empty.
     */
    void get(
            Collection<? extends ArtifactDownload> artifactDownloads,
            Collection<? extends MetadataDownload> metadataDownloads);

    /**
     * Performs the specified uploads. If an upload fails, the connector stores the underlying exception in the upload
     * object such that callers can inspect the result via {@link ArtifactUpload#getException()} and
     * {@link MetadataUpload#getException()}, respectively. The connector may perform the transfers concurrently and in
     * any order.
     *
     * @param artifactUploads The artifact uploads to perform, may be {@code null} or empty.
     * @param metadataUploads The metadata uploads to perform, may be {@code null} or empty.
     */
    void put(
            Collection<? extends ArtifactUpload> artifactUploads, Collection<? extends MetadataUpload> metadataUploads);

    /**
     * Closes this connector and frees any network resources associated with it. Once closed, a connector must not be
     * used for further transfers, any attempt to do so would yield a {@link IllegalStateException} or similar. Closing
     * an already closed connector is harmless and has no effect.
     */
    void close();
}
