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
package org.eclipse.aether.connector.file;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

/**
 * Helper for {@link TransferEvent}-handling.
 */
class TransferEventCatapult
{

    private TransferListener listener;

    public TransferEventCatapult( TransferListener listener )
    {
        if ( listener == null )
        {
            this.listener = new NoTransferListener();
        }
        else
        {
            this.listener = listener;
        }
    }

    protected void fireInitiated( TransferEvent.Builder event )
        throws TransferCancelledException
    {
        event.setType( TransferEvent.EventType.INITIATED );
        listener.transferInitiated( event.build() );
    }

    protected void fireStarted( TransferEvent.Builder event )
        throws TransferCancelledException
    {
        event.setType( TransferEvent.EventType.STARTED );
        listener.transferStarted( event.build() );
    }

    protected void fireSucceeded( TransferEvent.Builder event )
    {
        event.setType( TransferEvent.EventType.SUCCEEDED );
        listener.transferSucceeded( event.build() );
    }

    protected void fireFailed( TransferEvent.Builder event )
    {
        event.setType( TransferEvent.EventType.FAILED );
        listener.transferFailed( event.build() );
    }

    protected void fireCorrupted( TransferEvent.Builder event )
        throws TransferCancelledException
    {
        event.setType( TransferEvent.EventType.CORRUPTED );
        listener.transferCorrupted( event.build() );
    }

    protected void fireProgressed( TransferEvent.Builder event )
        throws TransferCancelledException
    {
        event.setType( TransferEvent.EventType.PROGRESSED );
        listener.transferProgressed( event.build() );
    }

    private final class NoTransferListener
        extends AbstractTransferListener
    {
    }

}
