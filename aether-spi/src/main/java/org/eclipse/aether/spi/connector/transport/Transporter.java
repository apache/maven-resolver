/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.spi.connector.transport;

import java.io.Closeable;

/**
 * A transporter for a remote repository. A transporter is responsible for transferring resources between the remote
 * repository and the local system. During its operation, the transporter must provide progress feedback via the
 * {@link TransportListener} configured on the underlying task.
 * <p>
 * If applicable, a transporter should obey connect/request timeouts and other relevant settings from the
 * {@link org.eclipse.aether.RepositorySystemSession#getConfigProperties() configuration properties} of the repository
 * system session.
 * <p>
 * <strong>Note:</strong> Implementations must be thread-safe such that a given transporter instance can safely be used
 * for concurrent requests.
 */
public interface Transporter
    extends Closeable
{

    /**
     * Classification for exceptions that denote connectivity or authentication issues and any other kind of error that
     * is not mapped to another classification code.
     * 
     * @see #classify(Throwable)
     */
    public static final int ERROR_OTHER = 0;

    /**
     * Classification for exceptions that denote a requested resource does not exist in the remote repository. Note that
     * cases where a remote repository is completely inaccessible should be classified as {@link #ERROR_OTHER}.
     * 
     * @see #classify(Throwable)
     */
    public static final int ERROR_NOT_FOUND = 1;

    /**
     * Classifies the type of exception that has been thrown from a previous request to the transporter. The exception
     * types employed by a transporter are generally unknown to its caller. Where a caller needs to distinguish between
     * certain error cases, it employs this method to detect which error case corresponds to the exception.
     * 
     * @param error The exception to classify, must not be {@code null}.
     * @return The classification of the error, either {@link #ERROR_NOT_FOUND} or {@link #ERROR_OTHER}.
     */
    int classify( Throwable error );

    /**
     * Checks the existence of a resource in the repository. If the remote repository can be contacted successfully but
     * indicates the resource specified in the request does not exist, an exception is thrown such that invoking
     * {@link #classify(Throwable)} with that exception yields {@link #ERROR_NOT_FOUND}.
     * 
     * @param task The existence check to perform, must not be {@code null}.
     * @throws Exception If the existence of the specified resource could not be confirmed.
     */
    void peek( PeekTask task )
        throws Exception;

    /**
     * Downloads a resource from the repository. If the resource is downloaded to a file as given by
     * {@link GetTask#getDataFile()} and the operation fails midway, the transporter should not delete the partial file
     * but leave its management to the caller.
     * 
     * @param task The download to perform, must not be {@code null}.
     * @throws Exception If the transfer failed.
     */
    void get( GetTask task )
        throws Exception;

    /**
     * Uploads a resource to the repository.
     * 
     * @param task The upload to perform, must not be {@code null}.
     * @throws Exception If the transfer failed.
     */
    void put( PutTask task )
        throws Exception;

    /**
     * Closes this transporter and frees any network resources associated with it. Once closed, a transporter must not
     * be used for further transfers, any attempt to do so would yield a {@link IllegalStateException} or similar.
     * Closing an already closed transporter is harmless and has no effect.
     */
    void close();

}
