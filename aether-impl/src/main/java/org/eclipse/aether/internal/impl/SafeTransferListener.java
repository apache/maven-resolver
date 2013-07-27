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
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

class SafeTransferListener
    extends AbstractTransferListener
{

    private final Logger logger;

    private final TransferListener listener;

    public static TransferListener wrap( RepositorySystemSession session, Logger logger )
    {
        TransferListener listener = session.getTransferListener();
        if ( listener == null )
        {
            return null;
        }
        return new SafeTransferListener( listener, logger );
    }

    protected SafeTransferListener( RepositorySystemSession session, Logger logger )
    {
        this( session.getTransferListener(), logger );
    }

    private SafeTransferListener( TransferListener listener, Logger logger )
    {
        this.listener = listener;
        this.logger = logger;
    }

    private void logError( TransferEvent event, Throwable e )
    {
        String msg = "Failed to dispatch transfer event '" + event + "' to " + listener.getClass().getCanonicalName();
        logger.debug( msg, e );
    }

    @Override
    public void transferInitiated( TransferEvent event )
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            try
            {
                listener.transferInitiated( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

    @Override
    public void transferStarted( TransferEvent event )
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            try
            {
                listener.transferStarted( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

    @Override
    public void transferProgressed( TransferEvent event )
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            try
            {
                listener.transferProgressed( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

    @Override
    public void transferCorrupted( TransferEvent event )
        throws TransferCancelledException
    {
        if ( listener != null )
        {
            try
            {
                listener.transferCorrupted( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

    @Override
    public void transferSucceeded( TransferEvent event )
    {
        if ( listener != null )
        {
            try
            {
                listener.transferSucceeded( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

    @Override
    public void transferFailed( TransferEvent event )
    {
        if ( listener != null )
        {
            try
            {
                listener.transferFailed( event );
            }
            catch ( RuntimeException e )
            {
                logError( event, e );
            }
        }
    }

}
