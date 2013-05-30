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
package org.eclipse.aether.transport.wagon;

import java.nio.ByteBuffer;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;

/**
 * A wagon transfer listener that forwards events to a transport listener.
 */
final class WagonTransferListener
    extends AbstractTransferListener
{

    private final TransportListener listener;

    public WagonTransferListener( TransportListener listener )
    {
        this.listener = listener;
    }

    @Override
    public void transferStarted( TransferEvent event )
    {
        try
        {
            listener.transportStarted( 0, event.getResource().getContentLength() );
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
        try
        {
            listener.transportProgressed( ByteBuffer.wrap( buffer, 0, length ) );
        }
        catch ( TransferCancelledException e )
        {
            throw new WagonCancelledException( e );
        }
    }

}
