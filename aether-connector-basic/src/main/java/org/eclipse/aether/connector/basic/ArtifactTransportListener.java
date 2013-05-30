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
import org.eclipse.aether.spi.connector.ArtifactTransfer;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

final class ArtifactTransportListener
    extends TransferTransportListener<ArtifactTransfer>
{

    private final RemoteRepository repository;

    public ArtifactTransportListener( ArtifactTransfer transfer, RemoteRepository repository,
                                      TransferListener listener, TransferEvent.Builder eventBuilder )
    {
        super( transfer, listener, eventBuilder );
        this.repository = repository;
    }

    @Override
    public void transferFailed( Exception exception, int classification )
    {
        ArtifactTransferException e;
        if ( classification == Transporter.ERROR_NOT_FOUND )
        {
            e = new ArtifactNotFoundException( getTransfer().getArtifact(), repository );
        }
        else
        {
            e = new ArtifactTransferException( getTransfer().getArtifact(), repository, exception );
        }
        getTransfer().setException( e );
        super.transferFailed( e, classification );
    }

}
