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
package org.eclipse.aether.transfer;

import java.nio.ByteBuffer;

import org.eclipse.aether.RepositorySystemSession;

/**
 * An event fired to a transfer listener during an artifact/metadata transfer.
 * 
 * @see TransferListener
 * @see TransferEvent.Builder
 */
public final class TransferEvent
{

    /**
     * The type of the event.
     */
    public enum EventType
    {

        /**
         * @see TransferListener#transferInitiated(TransferEvent)
         */
        INITIATED,

        /**
         * @see TransferListener#transferStarted(TransferEvent)
         */
        STARTED,

        /**
         * @see TransferListener#transferProgressed(TransferEvent)
         */
        PROGRESSED,

        /**
         * @see TransferListener#transferCorrupted(TransferEvent)
         */
        CORRUPTED,

        /**
         * @see TransferListener#transferSucceeded(TransferEvent)
         */
        SUCCEEDED,

        /**
         * @see TransferListener#transferFailed(TransferEvent)
         */
        FAILED

    }

    /**
     * The type of the request/transfer being performed.
     */
    public enum RequestType
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

    private final EventType type;

    private final RequestType requestType;

    private final RepositorySystemSession session;

    private final TransferResource resource;

    private final ByteBuffer dataBuffer;

    private final long transferredBytes;

    private final Exception exception;

    TransferEvent( Builder builder )
    {
        type = builder.type;
        requestType = builder.requestType;
        session = builder.session;
        resource = builder.resource;
        dataBuffer = builder.dataBuffer;
        transferredBytes = builder.transferredBytes;
        exception = builder.exception;
    }

    /**
     * Gets the type of the event.
     * 
     * @return The type of the event, never {@code null}.
     */
    public EventType getType()
    {
        return type;
    }

    /**
     * Gets the type of the request/transfer.
     * 
     * @return The type of the request/transfer, never {@code null}.
     */
    public RequestType getRequestType()
    {
        return requestType;
    }

    /**
     * Gets the repository system session during which the event occurred.
     * 
     * @return The repository system session during which the event occurred, never {@code null}.
     */
    public RepositorySystemSession getSession()
    {
        return session;
    }

    /**
     * Gets the resource that is being transferred.
     * 
     * @return The resource being transferred, never {@code null}.
     */
    public TransferResource getResource()
    {
        return resource;
    }

    /**
     * Gets the total number of bytes that have been transferred since the download/upload was started.
     * 
     * @return The total number of bytes that have been transferred since the transfer started, never negative.
     * @see #getDataLength()
     */
    public long getTransferredBytes()
    {
        return transferredBytes;
    }

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
    public ByteBuffer getDataBuffer()
    {
        return ( dataBuffer != null ) ? dataBuffer.asReadOnlyBuffer() : null;
    }

    /**
     * Gets the number of bytes that have been transferred since the last event.
     * 
     * @return The number of bytes that have been transferred since the last event, possibly zero but never negative.
     * @see #getTransferredBytes()
     */
    public int getDataLength()
    {
        return ( dataBuffer != null ) ? dataBuffer.remaining() : 0;
    }

    /**
     * Gets the error that occurred during the transfer.
     * 
     * @return The error that occurred or {@code null} if none.
     */
    public Exception getException()
    {
        return exception;
    }

    @Override
    public String toString()
    {
        return getRequestType() + " " + getType() + " " + getResource();
    }

    /**
     * A builder to create transfer events.
     */
    public static final class Builder
    {

        EventType type;

        RequestType requestType;

        RepositorySystemSession session;

        TransferResource resource;

        ByteBuffer dataBuffer;

        long transferredBytes;

        Exception exception;

        /**
         * Creates a new transfer event builder for the specified session and the given resource.
         * 
         * @param session The repository system session, must not be {@code null}.
         * @param resource The resource being transferred, must not be {@code null}.
         */
        public Builder( RepositorySystemSession session, TransferResource resource )
        {
            if ( session == null )
            {
                throw new IllegalArgumentException( "session not specified" );
            }
            if ( resource == null )
            {
                throw new IllegalArgumentException( "transfer resource not specified" );
            }
            this.session = session;
            this.resource = resource;
            type = EventType.INITIATED;
            requestType = RequestType.GET;
        }

        private Builder( Builder prototype )
        {
            session = prototype.session;
            resource = prototype.resource;
            type = prototype.type;
            requestType = prototype.requestType;
            dataBuffer = prototype.dataBuffer;
            transferredBytes = prototype.transferredBytes;
            exception = prototype.exception;
        }

        /**
         * Creates a new transfer event builder from the current values of this builder. The state of this builder
         * remains unchanged.
         * 
         * @return The new event builder, never {@code null}.
         */
        public Builder copy()
        {
            return new Builder( this );
        }

        /**
         * Sets the type of the event and resets event-specific fields. In more detail, the data buffer and the
         * exception fields are set to {@code null}. Furthermore, the total number of transferred bytes is set to
         * {@code 0} if the event type is {@link EventType#STARTED}.
         * 
         * @param type The type of the event, must not be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder resetType( EventType type )
        {
            if ( type == null )
            {
                throw new IllegalArgumentException( "event type not specified" );
            }
            this.type = type;
            dataBuffer = null;
            exception = null;
            switch ( type )
            {
                case INITIATED:
                case STARTED:
                    transferredBytes = 0;
                default:
            }
            return this;
        }

        /**
         * Sets the type of the event. When re-using the same builder to generate a sequence of events for one transfer,
         * {@link #resetType(TransferEvent.EventType)} might be more handy.
         * 
         * @param type The type of the event, must not be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setType( EventType type )
        {
            if ( type == null )
            {
                throw new IllegalArgumentException( "event type not specified" );
            }
            this.type = type;
            return this;
        }

        /**
         * Sets the type of the request/transfer.
         * 
         * @param requestType The request/transfer type, must not be {@code null}.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setRequestType( RequestType requestType )
        {
            if ( requestType == null )
            {
                throw new IllegalArgumentException( "request type not specified" );
            }
            this.requestType = requestType;
            return this;
        }

        /**
         * Sets the total number of bytes that have been transferred so far during the download/upload.
         * 
         * @param transferredBytes The total number of bytes that have been transferred so far during the
         *            download/upload, must not be negative.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setTransferredBytes( long transferredBytes )
        {
            if ( transferredBytes < 0 )
            {
                throw new IllegalArgumentException( "number of transferred bytes cannot be negative" );
            }
            this.transferredBytes = transferredBytes;
            return this;
        }

        /**
         * Increments the total number of bytes that have been transferred so far during the download/upload.
         * 
         * @param transferredBytes The number of bytes that have been transferred since the last event, must not be
         *            negative.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder addTransferredBytes( long transferredBytes )
        {
            if ( transferredBytes < 0 )
            {
                throw new IllegalArgumentException( "number of transferred bytes cannot be negative" );
            }
            this.transferredBytes += transferredBytes;
            return this;
        }

        /**
         * Sets the byte buffer holding the transferred bytes since the last event.
         * 
         * @param buffer The byte buffer holding the transferred bytes since the last event, may be {@code null} if not
         *            applicable to the event.
         * @param offset The starting point of valid bytes in the array.
         * @param length The number of valid bytes, must not be negative.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setDataBuffer( byte[] buffer, int offset, int length )
        {
            return setDataBuffer( ( buffer != null ) ? ByteBuffer.wrap( buffer, offset, length ) : null );
        }

        /**
         * Sets the byte buffer holding the transferred bytes since the last event.
         * 
         * @param dataBuffer The byte buffer holding the transferred bytes since the last event, may be {@code null} if
         *            not applicable to the event.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setDataBuffer( ByteBuffer dataBuffer )
        {
            this.dataBuffer = dataBuffer;
            return this;
        }

        /**
         * Sets the error that occurred during the transfer.
         * 
         * @param exception The error that occurred during the transfer, may be {@code null} if none.
         * @return This event builder for chaining, never {@code null}.
         */
        public Builder setException( Exception exception )
        {
            this.exception = exception;
            return this;
        }

        /**
         * Builds a new transfer event from the current values of this builder. The state of the builder itself remains
         * unchanged.
         * 
         * @return The transfer event, never {@code null}.
         */
        public TransferEvent build()
        {
            return new TransferEvent( this );
        }

    }

}
