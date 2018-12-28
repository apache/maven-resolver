package org.eclipse.aether.util.listener;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

/**
 * A transfer listener that delegates to zero or more other listeners (multicast). The list of target listeners is
 * thread-safe, i.e. target listeners can be added or removed by any thread at any time.
 */
public final class ChainedTransferListener
    extends AbstractTransferListener
{

    private final List<TransferListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new multicast listener that delegates to the specified listeners. In contrast to the constructor, this
     * factory method will avoid creating an actual chained listener if one of the specified readers is actually
     * {@code null}.
     * 
     * @param listener1 The first listener, may be {@code null}.
     * @param listener2 The second listener, may be {@code null}.
     * @return The chained listener or {@code null} if no listener was supplied.
     */
    public static TransferListener newInstance( TransferListener listener1, TransferListener listener2 )
    {
        if ( listener1 == null )
        {
            return listener2;
        }
        else if ( listener2 == null )
        {
            return listener1;
        }
        return new ChainedTransferListener( listener1, listener2 );
    }

    /**
     * Creates a new multicast listener that delegates to the specified listeners.
     * 
     * @param listeners The listeners to delegate to, may be {@code null} or empty.
     */
    public ChainedTransferListener( TransferListener... listeners )
    {
        if ( listeners != null )
        {
            add( Arrays.asList( listeners ) );
        }
    }

    /**
     * Creates a new multicast listener that delegates to the specified listeners.
     * 
     * @param listeners The listeners to delegate to, may be {@code null} or empty.
     */
    public ChainedTransferListener( Collection<? extends TransferListener> listeners )
    {
        add( listeners );
    }

    /**
     * Adds the specified listeners to the end of the multicast chain.
     * 
     * @param listeners The listeners to add, may be {@code null} or empty.
     */
    public void add( Collection<? extends TransferListener> listeners )
    {
        if ( listeners != null )
        {
            for ( TransferListener listener : listeners )
            {
                add( listener );
            }
        }
    }

    /**
     * Adds the specified listener to the end of the multicast chain.
     * 
     * @param listener The listener to add, may be {@code null}.
     */
    public void add( TransferListener listener )
    {
        if ( listener != null )
        {
            listeners.add( listener );
        }
    }

    /**
     * Removes the specified listener from the multicast chain. Trying to remove a non-existing listener has no effect.
     * 
     * @param listener The listener to remove, may be {@code null}.
     */
    public void remove( TransferListener listener )
    {
        if ( listener != null )
        {
            listeners.remove( listener );
        }
    }

    protected void handleError( TransferEvent event, TransferListener listener, RuntimeException error )
    {
        // default just swallows errors
    }

    @Override
    public void transferInitiated( TransferEvent event )
        throws TransferCancelledException
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferInitiated( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void transferStarted( TransferEvent event )
        throws TransferCancelledException
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferStarted( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void transferProgressed( TransferEvent event )
        throws TransferCancelledException
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferProgressed( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void transferCorrupted( TransferEvent event )
        throws TransferCancelledException
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferCorrupted( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void transferSucceeded( TransferEvent event )
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferSucceeded( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void transferFailed( TransferEvent event )
    {
        for ( TransferListener listener : listeners )
        {
            try
            {
                listener.transferFailed( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

}
