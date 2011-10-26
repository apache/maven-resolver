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
package org.eclipse.aether.transfer;


/**
 * A listener being notified of artifact/metadata transfers from/to remote repositories. The listener may be called from
 * an arbitrary thread. Reusing common regular expression syntax, the sequence of events is roughly as follows:
 * 
 * <pre>
 * INITIATED ( STARTED PROGRESSED* CORRUPTED? )* ( SUCCEEDED | FAILED )
 * </pre>
 * 
 * <em>Note:</em> Implementors are strongly advised to inherit from {@link AbstractTransferListener} instead of directly
 * implementing this interface.
 */
public interface TransferListener
{

    /**
     * Notifies the listener about the initiation of a transfer. This event gets fired before any actual network access
     * to the remote repository.
     * 
     * @param event The event details, must not be {@code null}.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    void transferInitiated( TransferEvent event )
        throws TransferCancelledException;

    /**
     * Notifies the listener about the start of a data transfer, i.e. the successful connection to the remote
     * repository.
     * 
     * @param event The event details, must not be {@code null}.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    void transferStarted( TransferEvent event )
        throws TransferCancelledException;

    /**
     * Notifies the listener about some progress in the data transfer. This event may even be fired if actually zero
     * bytes have been transferred since the last event, for instance to enable cancellation.
     * 
     * @param event The event details, must not be {@code null}.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    void transferProgressed( TransferEvent event )
        throws TransferCancelledException;

    /**
     * Notifies the listener that a checksum validation failed. {@link TransferEvent#getException()} will be of type
     * {@link ChecksumFailureException} and can be used to query further details about the expected/actual checksums.
     * 
     * @param event The event details, must not be {@code null}.
     * @throws TransferCancelledException If the transfer should be aborted.
     */
    void transferCorrupted( TransferEvent event )
        throws TransferCancelledException;

    /**
     * Notifies the listener about the successful completion of a transfer.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void transferSucceeded( TransferEvent event );

    /**
     * Notifies the listener about the unsuccessful termination of a transfer. {@link TransferEvent#getException()} will
     * provide further information about the failure.
     * 
     * @param event The event details, must not be {@code null}.
     */
    void transferFailed( TransferEvent event );

}
