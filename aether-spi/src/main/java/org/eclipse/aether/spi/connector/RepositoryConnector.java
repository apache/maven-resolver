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

import java.util.Collection;

/**
 * A connector for a remote repository. The connector is responsible for downloading/uploading of artifacts and metadata
 * from/to a remote repository. Besides performing the actual transfer and recording any exception encountered in the
 * provided upload/download objects, a connector must also use
 * {@link Transfer#setState(org.eclipse.aether.spi.connector.Transfer.State)} to update the state of a transfer during
 * its processing. Furthermore, the connector must notify any {@link org.eclipse.aether.transfer.TransferListener
 * TransferListener} configured on its associated {@link org.eclipse.aether.RepositorySystemSession
 * RepositorySystemSession}. If applicable, a connector should obey connect/request timeouts and other relevant settings
 * from the configuration properties of the repository system session. While a connector itself can use multiple threads
 * internally to performs the transfers, clients must not call a connector concurrently, i.e. connectors are generally
 * not thread-safe.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getConfigProperties()
 */
public interface RepositoryConnector
{

    /**
     * Performs the specified downloads. Any error encountered during a transfer can later be queried via
     * {@link ArtifactDownload#getException()} and {@link MetadataDownload#getException()}, respectively. The connector
     * may perform the transfers concurrently and in any order.
     * 
     * @param artifactDownloads The artifact downloads to perform, may be {@code null} or empty.
     * @param metadataDownloads The metadata downloads to perform, may be {@code null} or empty.
     */
    void get( Collection<? extends ArtifactDownload> artifactDownloads,
              Collection<? extends MetadataDownload> metadataDownloads );

    /**
     * Performs the specified uploads. Any error encountered during a transfer can later be queried via
     * {@link ArtifactDownload#getException()} and {@link MetadataDownload#getException()}, respectively. The connector
     * may perform the transfers concurrently and in any order.
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
