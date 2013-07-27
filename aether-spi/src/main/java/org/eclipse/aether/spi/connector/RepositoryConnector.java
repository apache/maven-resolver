/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
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
 */
public interface RepositoryConnector
    extends Closeable
{

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
    void get( Collection<? extends ArtifactDownload> artifactDownloads,
              Collection<? extends MetadataDownload> metadataDownloads );

    /**
     * Performs the specified uploads. If an upload fails, the connector stores the underlying exception in the upload
     * object such that callers can inspect the result via {@link ArtifactUpload#getException()} and
     * {@link MetadataUpload#getException()}, respectively. The connector may perform the transfers concurrently and in
     * any order.
     * 
     * @param artifactUploads The artifact uploads to perform, may be {@code null} or empty.
     * @param metadataUploads The metadata uploads to perform, may be {@code null} or empty.
     */
    void put( Collection<? extends ArtifactUpload> artifactUploads, Collection<? extends MetadataUpload> metadataUploads );

    /**
     * Closes this connector and frees any network resources associated with it. Once closed, a connector must not be
     * used for further transfers, any attempt to do so would yield a {@link IllegalStateException} or similar. Closing
     * an already closed connector is harmless and has no effect.
     */
    void close();

}
