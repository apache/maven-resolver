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
package org.eclipse.aether.connector.wagon;

import java.io.File;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferEvent.RequestType;
import org.eclipse.aether.util.listener.DefaultTransferEvent;
import org.eclipse.aether.util.listener.DefaultTransferResource;

/**
 * An adapter to transform transfer events from Wagon into events for the repository system.
 */
class WagonTransferListenerAdapter
    extends AbstractTransferListener
{

    private final RequestType requestType;

    private final DefaultTransferResource resource;

    private final TransferListener delegate;

    private long transferredBytes;

    public WagonTransferListenerAdapter( TransferListener delegate, String repositoryUrl, String resourceName,
                                         File file, RequestTrace trace, RequestType requestType )
    {
        this.delegate = delegate;
        this.requestType = requestType;
        resource = new DefaultTransferResource( repositoryUrl, resourceName, file, trace );
    }

    @Override
    public void transferStarted( TransferEvent event )
    {
        transferredBytes = 0;
        resource.setContentLength( event.getResource().getContentLength() );
        try
        {
            delegate.transferStarted( wrap( event, EventType.STARTED ) );
        }
        catch ( TransferCancelledException e )
        {
            /*
             * NOTE: Wagon transfers are not freely abortable. In particular, aborting from
             * AbstractWagon.fire(Get|Put)Started() would result in unclosed streams so we avoid this case.
             */
        }
    }

    @Override
    public void transferProgress( TransferEvent event, byte[] buffer, int length )
    {
        transferredBytes += length;
        try
        {
            delegate.transferProgressed( wrap( event, EventType.PROGRESSED ).setDataBuffer( buffer, 0, length ) );
        }
        catch ( TransferCancelledException e )
        {
            throw new WagonCancelledException( e );
        }
    }

    private DefaultTransferEvent wrap( TransferEvent event, EventType type )
    {
        DefaultTransferEvent e = newEvent( type );
        e.setRequestType( event.getRequestType() == TransferEvent.REQUEST_PUT ? RequestType.PUT : RequestType.GET );
        return e;
    }

    public DefaultTransferEvent newEvent( EventType type )
    {
        DefaultTransferEvent e = new DefaultTransferEvent( type, resource );
        e.setRequestType( requestType );
        e.setTransferredBytes( transferredBytes );
        return e;
    }

}
