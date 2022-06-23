package org.apache.maven.resolver.connector.basic;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.resolver.spi.connector.Transfer;
import org.apache.maven.resolver.spi.connector.transport.TransportListener;
import org.apache.maven.resolver.transfer.TransferCancelledException;
import org.apache.maven.resolver.transfer.TransferEvent;
import org.apache.maven.resolver.transfer.TransferEvent.EventType;
import org.apache.maven.resolver.transfer.TransferListener;

class TransferTransportListener<T extends Transfer>
    extends TransportListener
{

    private final T transfer;

    private final TransferListener listener;

    private final TransferEvent.Builder eventBuilder;

    private ChecksumCalculator checksumCalculator;

    protected TransferTransportListener( T transfer, TransferEvent.Builder eventBuilder )
    {
        this.transfer = transfer;
        this.listener = transfer.getListener();
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
    }

    @Override
    public void transportStarted( long dataOffset, long dataLength )
        throws TransferCancelledException
    {
        if ( checksumCalculator != null )
        {
            checksumCalculator.init( dataOffset );
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
        if ( checksumCalculator != null )
        {
            checksumCalculator.update( data );
        }
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.PROGRESSED ).addTransferredBytes( data.remaining() )
                    .setDataBuffer( data );
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
    }

    public void transferSucceeded()
    {
        if ( listener != null )
        {
            eventBuilder.resetType( EventType.SUCCEEDED );
            listener.transferSucceeded( eventBuilder.build() );
        }
    }

    public Map<String, Object> getChecksums()
    {
        if ( checksumCalculator == null )
        {
            return Collections.emptyMap();
        }
        return checksumCalculator.get();
    }

    public void setChecksumCalculator( ChecksumCalculator checksumCalculator )
    {
        this.checksumCalculator = checksumCalculator;
    }

}
