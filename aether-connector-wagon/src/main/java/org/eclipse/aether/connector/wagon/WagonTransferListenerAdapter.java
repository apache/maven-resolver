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
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent.Builder;
import org.eclipse.aether.transfer.TransferEvent.EventType;
import org.eclipse.aether.transfer.TransferEvent.RequestType;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;

/**
 * An adapter to transform transfer events from Wagon into events for the repository system.
 */
class WagonTransferListenerAdapter
    extends AbstractTransferListener
{

    private final TransferResource resource;

    private final TransferListener delegate;

    private final Builder eventBuilder;

    public WagonTransferListenerAdapter( TransferListener delegate, String repositoryUrl, String resourceName,
                                         File file, RequestTrace trace, RequestType requestType,
                                         RepositorySystemSession session )
    {
        this.delegate = delegate;
        resource = new TransferResource( repositoryUrl, resourceName, file, trace );
        eventBuilder = new Builder( session, resource ).setRequestType( requestType );
    }

    @Override
    public void transferStarted( TransferEvent event )
    {
        eventBuilder.setTransferredBytes( 0 );
        resource.setContentLength( event.getResource().getContentLength() );
        try
        {
            delegate.transferStarted( newEvent( EventType.STARTED ).build() );
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
        eventBuilder.addTransferredBytes( length );
        try
        {
            delegate.transferProgressed( newEvent( EventType.PROGRESSED ).setDataBuffer( buffer, 0, length ).build() );
        }
        catch ( TransferCancelledException e )
        {
            throw new WagonCancelledException( e );
        }
    }

    public Builder newEvent( EventType type )
    {
        return eventBuilder.copy().setType( type );
    }

}
