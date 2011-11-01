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
package org.eclipse.aether.internal.test.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;

public class RecordingTransferListener
    implements TransferListener
{

    private List<TransferEvent> events = Collections.synchronizedList( new ArrayList<TransferEvent>() );

    private List<TransferEvent> progressEvents = Collections.synchronizedList( new ArrayList<TransferEvent>() );

    private TransferListener realListener;

    public RecordingTransferListener()
    {
        this( null );
    }

    public RecordingTransferListener( TransferListener transferListener )
    {
        this.realListener = transferListener;
    }

    public List<TransferEvent> getEvents()
    {
        return events;
    }

    public List<TransferEvent> getProgressEvents()
    {
        return progressEvents;
    }

    public void transferSucceeded( TransferEvent event )
    {
        events.add( event );
        if ( realListener != null )
        {
            realListener.transferSucceeded( event );
        }
    }

    public void transferStarted( TransferEvent event )
        throws TransferCancelledException
    {
        events.add( event );
        if ( realListener != null )
        {
            realListener.transferStarted( event );
        }
    }

    public void transferProgressed( TransferEvent event )
        throws TransferCancelledException
    {
        event = new RecordedTransferEvent( event );
        events.add( event );
        progressEvents.add( event );
        if ( realListener != null )
        {
            realListener.transferProgressed( event );
        }
    }

    public void transferInitiated( TransferEvent event )
        throws TransferCancelledException
    {
        events.add( event );
        if ( realListener != null )
        {
            realListener.transferInitiated( event );
        }
    }

    public void transferFailed( TransferEvent event )
    {
        events.add( event );
        if ( realListener != null )
        {
            realListener.transferFailed( event );
        }
    }

    public void transferCorrupted( TransferEvent event )
        throws TransferCancelledException
    {
        events.add( event );
        if ( realListener != null )
        {
            realListener.transferCorrupted( event );
        }
    }

    public void clear()
    {
        events.clear();
        progressEvents.clear();
    }

    static class RecordedTransferEvent
        implements TransferEvent
    {

        private final TransferEvent event;

        private final ByteBuffer buffer;

        public RecordedTransferEvent( TransferEvent event )
        {
            this.event = event;

            // the buffer may be reused for future events so we need to clone it for later inspection
            ByteBuffer buffer = event.getDataBuffer();
            if ( buffer != null )
            {
                this.buffer = ByteBuffer.allocate( buffer.remaining() );
                this.buffer.put( buffer );
                this.buffer.flip();
            }
            else
            {
                this.buffer = null;
            }
        }

        public EventType getType()
        {
            return event.getType();
        }

        public RequestType getRequestType()
        {
            return event.getRequestType();
        }

        public TransferResource getResource()
        {
            return event.getResource();
        }

        public long getTransferredBytes()
        {
            return event.getTransferredBytes();
        }

        public ByteBuffer getDataBuffer()
        {
            return ( buffer != null ) ? buffer.asReadOnlyBuffer() : null;
        }

        public int getDataLength()
        {
            return ( buffer != null ) ? buffer.remaining() : 0;
        }

        public Exception getException()
        {
            return event.getException();
        }

    }

}
