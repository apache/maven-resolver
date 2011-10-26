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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import com.ning.http.client.generators.FileBodyGenerator;

import org.eclipse.aether.transfer.TransferCancelledException;

import com.ning.http.client.RandomAccessBody;

class ProgressingFileBodyGenerator
    extends FileBodyGenerator
{

    private final CompletionHandler completionHandler;

    public ProgressingFileBodyGenerator( File file, CompletionHandler completionHandler )
    {
        super( file );
        this.completionHandler = completionHandler;
    }

    @Override
    public RandomAccessBody createBody()
        throws IOException
    {
        return new ProgressingBody( super.createBody() );
    }

    final class ProgressingBody
        implements RandomAccessBody
    {

        final RandomAccessBody delegate;

        private ProgressingWritableByteChannel channel;

        public ProgressingBody( RandomAccessBody delegate )
        {
            this.delegate = delegate;
        }

        public long getContentLength()
        {
            return delegate.getContentLength();
        }

        public long read( ByteBuffer buffer )
            throws IOException
        {
            ByteBuffer event = buffer.slice();
            long read = delegate.read( buffer );
            if ( read > 0 )
            {
                try
                {
                    event.limit( (int) read );
                    completionHandler.fireTransferProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw (IOException) new IOException( e.getMessage() ).initCause( e );
                }
            }
            return read;
        }

        public long transferTo( long position, long count, WritableByteChannel target )
            throws IOException
        {
            ProgressingWritableByteChannel dst = channel;
            if ( dst == null || dst.delegate != target )
            {
                channel = dst = new ProgressingWritableByteChannel( target );
            }
            return delegate.transferTo( position, Math.min( count, 1024 * 16 ), dst );
        }

        public void close()
            throws IOException
        {
            delegate.close();
        }

    }

    final class ProgressingWritableByteChannel
        implements WritableByteChannel
    {

        final WritableByteChannel delegate;

        public ProgressingWritableByteChannel( WritableByteChannel delegate )
        {
            this.delegate = delegate;
        }

        public boolean isOpen()
        {
            return delegate.isOpen();
        }

        public void close()
            throws IOException
        {
            delegate.close();
        }

        public int write( ByteBuffer src )
            throws IOException
        {
            ByteBuffer event = src.slice();
            int written = delegate.write( src );
            if ( written > 0 )
            {
                try
                {
                    event.limit( written );
                    completionHandler.fireTransferProgressed( event );
                }
                catch ( TransferCancelledException e )
                {
                    throw (IOException) new IOException( e.getMessage() ).initCause( e );
                }
            }
            return written;
        }

    }

}
