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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;

/**
 * A repository listener that delegates to zero or more other listeners (multicast). The list of target listeners is
 * thread-safe, i.e. target listeners can be added or removed by any thread at any time.
 */
public final class ChainedRepositoryListener
    extends AbstractRepositoryListener
{

    private final List<RepositoryListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a new multicast listener that delegates to the specified listeners. In contrast to the constructor, this
     * factory method will avoid creating an actual chained listener if one of the specified readers is actually
     * {@code null}.
     * 
     * @param listener1 The first listener, may be {@code null}.
     * @param listener2 The second listener, may be {@code null}.
     * @return The chained listener or {@code null} if no listener was supplied.
     */
    public static RepositoryListener newInstance( RepositoryListener listener1, RepositoryListener listener2 )
    {
        if ( listener1 == null )
        {
            return listener2;
        }
        else if ( listener2 == null )
        {
            return listener1;
        }
        return new ChainedRepositoryListener( listener1, listener2 );
    }

    /**
     * Creates a new multicast listener that delegates to the specified listeners.
     * 
     * @param listeners The listeners to delegate to, may be {@code null} or empty.
     */
    public ChainedRepositoryListener( RepositoryListener... listeners )
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
    public ChainedRepositoryListener( Collection<? extends RepositoryListener> listeners )
    {
        add( listeners );
    }

    /**
     * Adds the specified listeners to the end of the multicast chain.
     * 
     * @param listeners The listeners to add, may be {@code null} or empty.
     */
    public void add( Collection<? extends RepositoryListener> listeners )
    {
        if ( listeners != null )
        {
            for ( RepositoryListener listener : listeners )
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
    public void add( RepositoryListener listener )
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
    public void remove( RepositoryListener listener )
    {
        if ( listener != null )
        {
            listeners.remove( listener );
        }
    }

    @SuppressWarnings( "EmptyMethod" )
    protected void handleError( RepositoryEvent event, RepositoryListener listener, RuntimeException error )
    {
        // default just swallows errors
    }

    @Override
    public void artifactDeployed( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDeployed( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactDeploying( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDeploying( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactDescriptorInvalid( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDescriptorInvalid( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactDescriptorMissing( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDescriptorMissing( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactDownloaded( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDownloaded( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactDownloading( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactDownloading( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactInstalled( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactInstalled( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactInstalling( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactInstalling( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactResolved( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactResolved( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void artifactResolving( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.artifactResolving( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataDeployed( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataDeployed( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataDeploying( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataDeploying( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataDownloaded( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataDownloaded( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataDownloading( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataDownloading( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataInstalled( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataInstalled( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataInstalling( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataInstalling( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataInvalid( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataInvalid( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataResolved( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataResolved( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

    @Override
    public void metadataResolving( RepositoryEvent event )
    {
        for ( RepositoryListener listener : listeners )
        {
            try
            {
                listener.metadataResolving( event );
            }
            catch ( RuntimeException e )
            {
                handleError( event, listener, e );
            }
        }
    }

}
