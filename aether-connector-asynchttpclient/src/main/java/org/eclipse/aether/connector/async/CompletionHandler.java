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
package org.eclipse.aether.connector.async;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.ProgressAsyncHandler;
import com.ning.http.client.Response;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link AsyncHandler} for handling asynchronous download an upload.
 */
class CompletionHandler
    implements ProgressAsyncHandler<Response>
{
    private final Logger logger;

    private HttpResponseStatus status;

    private HttpResponseHeaders headers;

    private final ConcurrentLinkedQueue<TransferListener> listeners = new ConcurrentLinkedQueue<TransferListener>();

    private final AsyncHttpClient httpClient;

    private final AtomicLong byteTransfered = new AtomicLong();

    private final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

    private final TransferResource transferResource;

    private final TransferEvent.RequestType requestType;

    private final TransferEvent.Builder eventBuilder;

    public CompletionHandler( TransferResource transferResource, AsyncHttpClient httpClient, Logger logger,
                              TransferEvent.RequestType requestType, RepositorySystemSession session )
    {
        this.httpClient = httpClient;
        this.transferResource = transferResource;
        this.logger = logger;
        this.requestType = requestType;
        eventBuilder = new TransferEvent.Builder( session, transferResource ).setRequestType( requestType );
    }

    public STATE onHeaderWriteCompleted()
    {
        if ( TransferEvent.RequestType.PUT.equals( requestType ) )
        {
            byteTransfered.set( 0 );
            try
            {
                fireTransferStarted();
            }
            catch ( TransferCancelledException e )
            {
                return STATE.ABORT;
            }
        }
        return STATE.CONTINUE;
    }

    public STATE onContentWriteCompleted()
    {
        return STATE.CONTINUE;
    }

    public STATE onContentWriteProgress( long amount, long current, long total )
    {
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    /* @Override */
    public STATE onBodyPartReceived( final HttpResponseBodyPart content )
        throws Exception
    {
        try
        {
            fireTransferProgressed( content.getBodyPartBytes() );
        }
        catch ( TransferCancelledException e )
        {
            return STATE.ABORT;
        }
        catch ( Exception ex )
        {
            if ( logger.isDebugEnabled() )
            {
                logger.debug( "", ex );
            }
        }
        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    /* @Override */
    public STATE onStatusReceived( final HttpResponseStatus status )
        throws Exception
    {
        this.status = status;

        return ( status.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND ? STATE.ABORT : STATE.CONTINUE );
    }

    /**
     * {@inheritDoc}
     */
    /* @Override */
    public STATE onHeadersReceived( final HttpResponseHeaders headers )
        throws Exception
    {
        this.headers = headers;

        if ( !TransferEvent.RequestType.PUT.equals( requestType ) )
        {
            if ( status.getStatusCode() >= 200 && status.getStatusCode() < 300 )
            {
                try
                {
                    transferResource.setContentLength( Long.parseLong( headers.getHeaders().getFirstValue( "Content-Length" ) ) );
                }
                catch ( RuntimeException e )
                {
                    // oh well, no parsable content length
                }
                try
                {
                    fireTransferStarted();
                }
                catch ( TransferCancelledException e )
                {
                    return STATE.ABORT;
                }
            }
        }

        return STATE.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    /* @Override */
    public final Response onCompleted()
        throws Exception
    {
        // The connection has timed out
        if ( status == null )
        {
            throw new TransferException( "Invalid AHC State. Response will possibly gets corrupted." );
        }
        return onCompleted( httpClient.getProvider().prepareResponse( status, headers,
                                                                      Collections.<HttpResponseBodyPart> emptyList() ) );
    }

    /**
     * {@inheritDoc}
     */
    /* @Override */
    public void onThrowable( Throwable t )
    {
        exception.set( t );
    }

    /**
     * Invoked once the HTTP response has been fully read.
     * 
     * @param response The {@link com.ning.http.client.Response}
     * @return Type of the value that will be returned by the associated {@link java.util.concurrent.Future}
     */
    public Response onCompleted( Response response )
        throws Exception
    {
        if ( response != null && response.hasResponseStatus() && response.getStatusCode() >= HttpURLConnection.HTTP_OK
            && response.getStatusCode() <= HttpURLConnection.HTTP_CREATED )
        {
            fireTransferSucceeded( response );
        }
        return response;
    }

    void fireTransferProgressed( final byte[] buffer )
        throws TransferCancelledException
    {
        fireTransferProgressed( ByteBuffer.wrap( buffer ) );
    }

    void fireTransferProgressed( final ByteBuffer buffer )
        throws TransferCancelledException
    {
        final long bytesTransferred = byteTransfered.addAndGet( buffer.remaining() );

        final TransferEvent transferEvent =
            newEvent( TransferEvent.EventType.PROGRESSED ).setTransferredBytes( bytesTransferred ).setDataBuffer( buffer ).build();

        for ( final TransferListener listener : listeners )
        {
            listener.transferProgressed( transferEvent );
        }
    }

    void fireTransferSucceeded( final Response response )
        throws IOException
    {
        final long bytesTransferred = byteTransfered.get();

        final TransferEvent transferEvent =
            newEvent( TransferEvent.EventType.SUCCEEDED ).setTransferredBytes( bytesTransferred ).build();

        for ( final TransferListener listener : listeners )
        {
            listener.transferSucceeded( transferEvent );
        }
    }

    void fireTransferFailed()
        throws IOException
    {
        final long bytesTransferred = byteTransfered.get();

        final TransferEvent transferEvent =
            newEvent( TransferEvent.EventType.FAILED ).setTransferredBytes( bytesTransferred ).build();

        for ( final TransferListener listener : listeners )
        {
            listener.transferFailed( transferEvent );
        }
    }

    void fireTransferStarted()
        throws TransferCancelledException
    {
        final TransferEvent transferEvent =
            newEvent( TransferEvent.EventType.STARTED ).setTransferredBytes( 0 ).build();

        for ( final TransferListener listener : listeners )
        {
            listener.transferStarted( transferEvent );
        }
    }

    public boolean addTransferListener( TransferListener listener )
    {
        if ( listener == null )
        {
            return false;
        }
        return listeners.offer( listener );
    }

    public boolean removeTransferListener( TransferListener listener )
    {
        if ( listener == null )
        {
            return false;
        }
        return listeners.remove( listener );
    }

    protected HttpResponseStatus status()
    {
        return status;
    }

    private TransferEvent.Builder newEvent( TransferEvent.EventType type )
    {
        Throwable t = exception.get();
        return eventBuilder.copy().setType( type ).setException( t instanceof Exception ? (Exception) t
                                                                                 : new Exception( t ) );
    }

}
