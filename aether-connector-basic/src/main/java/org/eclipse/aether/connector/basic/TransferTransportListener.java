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
package org.eclipse.aether.connector.basic;

import java.nio.ByteBuffer;

import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferListener;

class TransferTransportListener<T extends Transfer>
    extends TransportListener
{

    private final T transfer;

    private final TransferListener listener;

    private final TransferEvent.Builder eventBuilder;

    private ChecksumCalculator checksums;

    protected TransferTransportListener( T transfer, TransferListener listener, TransferEvent.Builder eventBuilder )
    {
        this.transfer = transfer;
        this.listener = listener;
        this.eventBuilder = eventBuilder;
    }

    protected T getTransfer()
    {
        return transfer;
    }

    public void transferInitiated()
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.INITIATED );
            listener.transferInitiated( eventBuilder.build() );
        }
        transfer.setState( Transfer.State.ACTIVE );
    }

    @Override
    public void transportStarted( long dataOffset, long dataLength )
        throws TransferCancelledException
    {
        if ( checksums != null )
        {
            checksums.init( dataOffset );
        }
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.STARTED ).setTransferredBytes( dataOffset );
            TransferEvent event = eventBuilder.build();
            event.getResource().setContentLength( dataLength ).setResumeOffset( dataOffset );
            listener.transferStarted( event );
        }
    }

    @Override
    public void transportProgressed( ByteBuffer data )
        throws TransferCancelledException
    {
        if ( checksums != null )
        {
            checksums.update( data );
        }
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.PROGRESSED ).addTransferredBytes( data.remaining() ).setDataBuffer( data );
            listener.transferProgressed( eventBuilder.build() );
        }
    }

    public void transferCorrupted( Exception exception )
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.CORRUPTED ).setException( exception );
            listener.transferCorrupted( eventBuilder.build() );
        }
    }

    public void transferFailed( Exception exception, int classification )
    {
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.FAILED ).setException( exception );
            listener.transferFailed( eventBuilder.build() );
        }
        transfer.setState( Transfer.State.DONE );
    }

    public void transferSucceeded()
    {
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.SUCCEEDED );
            listener.transferSucceeded( eventBuilder.build() );
        }
        transfer.setState( Transfer.State.DONE );
    }

    public ChecksumCalculator getChecksums()
    {
        return checksums;
    }

    public void setChecksums( ChecksumCalculator checksums )
    {
        this.checksums = checksums;
    }

}
