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
package org.eclipse.aether.spi.connector.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.transfer.TransferCancelledException;

/**
 * A skeleton implementation for custom transporters.
 */
public abstract class AbstractTransporter
    implements Transporter
{

    private final AtomicBoolean closed;

    /**
     * Enables subclassing.
     */
    protected AbstractTransporter()
    {
        closed = new AtomicBoolean();
    }

    public void peek( PeekTask task )
        throws Exception
    {
        failIfClosed( task );
        implPeek( task );
    }

    /**
     * Implements {@link #peek(PeekTask)}, gets only called if the transporter has not been closed.
     * 
     * @param task The existence check to perform, must not be {@code null}.
     * @throws Exception If the existence of the specified resource could not be confirmed.
     */
    protected abstract void implPeek( PeekTask task )
        throws Exception;

    public void get( GetTask task )
        throws Exception
    {
        failIfClosed( task );
        implGet( task );
    }

    /**
     * Implements {@link #get(GetTask)}, gets only called if the transporter has not been closed.
     * 
     * @param task The download to perform, must not be {@code null}.
     * @throws Exception If the transfer failed.
     */
    protected abstract void implGet( GetTask task )
        throws Exception;

    /**
     * Performs stream-based I/O for the specified download task and notifies the configured transport listener.
     * Subclasses might want to invoke this utility method from within their {@link #implGet(GetTask)} to avoid
     * boilerplate I/O code.
     * 
     * @param task The download to perform, must not be {@code null}.
     * @param is The input stream to download the data from, must not be {@code null}.
     * @param close {@code true} if the supplied input stream should be automatically closed, {@code false} to leave the
     *            stream open.
     * @param length The size in bytes of the downloaded resource or {@code -1} if unknown, not to be confused with the
     *            length of the supplied input stream which might be smaller if the download is resumed.
     * @param resume {@code true} if the download resumes from {@link GetTask#getResumeOffset()}, {@code false} if the
     *            download starts at the first byte of the resource.
     * @throws IOException If the transfer encountered an I/O error.
     * @throws TransferCancelledException If the transfer was cancelled.
     */
    protected void utilGet( GetTask task, InputStream is, boolean close, long length, boolean resume )
        throws IOException, TransferCancelledException
    {
        try
        {
            task.getListener().transportStarted( resume ? task.getResumeOffset() : 0, length );
            OutputStream os = task.newOutputStream( resume );
            try
            {
                copy( os, is, task.getListener() );
                os.close();
            }
            finally
            {
                close( os );
            }
        }
        finally
        {
            if ( close )
            {
                close( is );
            }
        }
    }

    public void put( PutTask task )
        throws Exception
    {
        failIfClosed( task );
        implPut( task );
    }

    /**
     * Implements {@link #put(PutTask)}, gets only called if the transporter has not been closed.
     * 
     * @param task The upload to perform, must not be {@code null}.
     * @throws Exception If the transfer failed.
     */
    protected abstract void implPut( PutTask task )
        throws Exception;

    /**
     * Performs stream-based I/O for the specified upload task and notifies the configured transport listener.
     * Subclasses might want to invoke this utility method from within their {@link #implPut(PutTask)} to avoid
     * boilerplate I/O code.
     * 
     * @param task The upload to perform, must not be {@code null}.
     * @param os The output stream to upload the data to, must not be {@code null}.
     * @param close {@code true} if the supplied output stream should be automatically closed, {@code false} to leave
     *            the stream open.
     * @throws IOException If the transfer encountered an I/O error.
     * @throws TransferCancelledException If the transfer was cancelled.
     */
    protected void utilPut( PutTask task, OutputStream os, boolean close )
        throws IOException, TransferCancelledException
    {
        try
        {
            task.getListener().transportStarted( 0, task.getDataLength() );
            InputStream is = task.newInputStream();
            try
            {
                copy( os, is, task.getListener() );
            }
            finally
            {
                close( is );
            }
            if ( close )
            {
                os.close();
            }
            else
            {
                os.flush();
            }
        }
        finally
        {
            if ( close )
            {
                close( os );
            }
        }
    }

    public void close()
    {
        if ( closed.compareAndSet( false, true ) )
        {
            implClose();
        }
    }

    /**
     * Implements {@link #close()}, gets only called if the transporter has not already been closed.
     */
    protected abstract void implClose();

    private void failIfClosed( TransportTask task )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute task " + task );
        }
    }

    private static void copy( OutputStream os, InputStream is, TransportListener listener )
        throws IOException, TransferCancelledException
    {
        ByteBuffer buffer = ByteBuffer.allocate( 1024 * 32 );
        byte[] array = buffer.array();
        for ( int read = is.read( array ); read >= 0; read = is.read( array ) )
        {
            os.write( array, 0, read );
            buffer.rewind();
            buffer.limit( read );
            listener.transportProgressed( buffer );
        }
    }

    private static void close( Closeable file )
    {
        if ( file != null )
        {
            try
            {
                file.close();
            }
            catch ( IOException e )
            {
                // irrelevant
            }
        }
    }

}
