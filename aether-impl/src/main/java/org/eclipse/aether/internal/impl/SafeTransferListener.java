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
            catch ( LinkageError e )
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
            catch ( LinkageError e )
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
            catch ( LinkageError e )
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
            catch ( LinkageError e )
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
            catch ( LinkageError e )
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
            catch ( LinkageError e )
            {
                logError( event, e );
            }
        }
    }

}
