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

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.MetadataTransfer;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

final class MetadataTransportListener
    extends TransferTransportListener<MetadataTransfer>
{

    private final RemoteRepository repository;

    public MetadataTransportListener( MetadataTransfer transfer, RemoteRepository repository,
                                      TransferListener listener, TransferEvent.Builder eventBuilder )
    {
        super( transfer, listener, eventBuilder );
        this.repository = repository;
    }

    @Override
    public void transferFailed( Exception exception, int classification )
    {
        MetadataTransferException e;
        if ( classification == Transporter.ERROR_NOT_FOUND )
        {
            e = new MetadataNotFoundException( getTransfer().getMetadata(), repository );
        }
        else
        {
            e = new MetadataTransferException( getTransfer().getMetadata(), repository, exception );
        }
        getTransfer().setException( e );
        super.transferFailed( e, classification );
    }

}
