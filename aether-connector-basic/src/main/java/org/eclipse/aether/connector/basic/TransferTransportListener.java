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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.ChecksumUtils;

class TransferTransportListener<T extends Transfer>
    extends TransportListener
{

    private final T transfer;

    private final TransferListener listener;

    private final TransferEvent.Builder eventBuilder;

    private Map<String, MessageDigest> digests;

    protected TransferTransportListener( T transfer, TransferListener listener, TransferEvent.Builder eventBuilder )
    {
        this.transfer = transfer;
        this.listener = listener;
        this.eventBuilder = eventBuilder;
        this.digests = Collections.emptyMap();
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
        for ( MessageDigest digest : digests.values() )
        {
            digest.reset();
        }
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.STARTED ).setTransferredBytes( dataOffset );
            TransferEvent event = eventBuilder.build();
            event.getResource().setContentLength( dataLength );
            listener.transferStarted( event );
        }
    }

    @Override
    public void transportProgressed( ByteBuffer data )
        throws TransferCancelledException
    {
        for ( MessageDigest digest : digests.values() )
        {
            data.mark();
            digest.update( data );
            data.reset();
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

    public void addDigest( String algo )
    {
        try
        {
            if ( !digests.containsKey( algo ) )
            {
                MessageDigest digest = MessageDigest.getInstance( algo );
                if ( digests.isEmpty() )
                {
                    digests = new HashMap<String, MessageDigest>();
                }
                digests.put( algo, digest );
            }
        }
        catch ( NoSuchAlgorithmException e )
        {
            // treat this checksum as missing
        }
    }

    public Map<String, String> getDigests()
    {
        Map<String, String> results = new HashMap<String, String>();
        for ( Map.Entry<String, MessageDigest> entry : digests.entrySet() )
        {
            results.put( entry.getKey(), ChecksumUtils.toHexString( entry.getValue().digest() ) );
        }
        return results;
    }

}
