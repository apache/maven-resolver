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

import java.nio.ByteBuffer;

/**
 * An event fired to a transfer listener during an artifact/metadata transfer.
 * 
 * @see TransferListener
 */
public interface TransferEvent
{

    /**
     * The type of the event.
     */
    enum EventType
    {
        INITIATED, STARTED, PROGRESSED, CORRUPTED, SUCCEEDED, FAILED
    }

    /**
     * The type of the request/transfer being performed.
     */
    enum RequestType
    {

        /**
         * Download artifact/metadata.
         */
        GET,

        /**
         * Check artifact/metadata existence only.
         */
        GET_EXISTENCE,

        /**
         * Upload artifact/metadata.
         */
        PUT,

    }

    /**
     * Gets the type of the event.
     * 
     * @return The type of the event, never {@code null}.
     */
    EventType getType();

    /**
     * Gets the type of the request/transfer.
     * 
     * @return The type of the request/transfer, never {@code null}.
     */
    RequestType getRequestType();

    /**
     * Gets the resource that is being transferred.
     * 
     * @return The resource being transferred, never {@code null}.
     */
    TransferResource getResource();

    /**
     * Gets the total number of bytes that have been transferred since the download/upload was started.
     * 
     * @return The total number of bytes that have been transferred since the transfer started, never negative.
     * @see #getDataLength()
     */
    long getTransferredBytes();

    /**
     * Gets the byte buffer holding the transferred bytes since the last event. A listener must assume this buffer to be
     * owned by the event source and must not change any byte in this buffer. Also, the buffer is only valid for the
     * duration of the event callback, i.e. the next event might reuse the same buffer (with updated contents).
     * Therefore, if the actual event processing is deferred, the byte buffer would have to be cloned to create an
     * immutable snapshot of its contents.
     * 
     * @return The (read-only) byte buffer or {@code null} if not applicable to the event, i.e. if the event type is not
     *         {@link EventType#PROGRESSED}.
     */
    ByteBuffer getDataBuffer();

    /**
     * Gets the number of bytes that have been transferred since the last event.
     * 
     * @return The number of bytes that have been transferred since the last event, possibly zero but never negative.
     * @see #getTransferredBytes()
     */
    int getDataLength();

    /**
     * Gets the error that occurred during the transfer.
     * 
     * @return The error that occurred or {@code null} if none.
     */
    Exception getException();

}
