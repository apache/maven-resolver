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
package org.eclipse.aether.transport.file;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A transporter using {@link java.io.File}.
 */
final class FileTransporter
    implements Transporter
{

    private final Logger logger;

    private final File basedir;

    private final AtomicBoolean closed = new AtomicBoolean();

    public FileTransporter( RemoteRepository repository, Logger logger )
        throws NoTransporterException
    {
        if ( !"file".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }
        this.logger = logger;

        basedir = new File( PathUtils.basedir( repository.getUrl() ) ).getAbsoluteFile();
    }

    public int classify( Throwable error )
    {
        if ( error instanceof ResourceNotFoundException )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    public void peek( PeekTask task )
        throws Exception
    {
        failIfClosed( task );

        getFile( task, true );
    }

    public void get( GetTask task )
        throws Exception
    {
        failIfClosed( task );

        File file = getFile( task, true );
        task.getListener().transportStarted( 0, file.length() );
        InputStream is = new FileInputStream( file );
        try
        {
            OutputStream os = task.newOutputStream();
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
            close( is );
        }
    }

    public void put( PutTask task )
        throws Exception
    {
        failIfClosed( task );

        File file = getFile( task, false );
        task.getListener().transportStarted( 0, task.getDataLength() );
        file.getParentFile().mkdirs();
        OutputStream os = new FileOutputStream( file );
        try
        {
            try
            {
                InputStream is = task.newInputStream();
                try
                {
                    copy( os, is, task.getListener() );
                }
                finally
                {
                    close( is );
                }
                os.close();
            }
            finally
            {
                close( os );
            }
        }
        catch ( Exception e )
        {
            if ( !file.delete() && file.exists() )
            {
                logger.debug( "Could not delete partial file " + file );
            }
            throw e;
        }
    }

    private File getFile( TransportTask task, boolean required )
        throws Exception
    {
        String path = task.getLocation().getPath();
        if ( path.contains( "../" ) )
        {
            throw new IllegalArgumentException( "Illegal resource path: " + path );
        }
        File file = new File( basedir, path );
        if ( required && !file.exists() )
        {
            throw new ResourceNotFoundException( "Could not locate " + file );
        }
        return file;
    }

    private static void copy( OutputStream os, InputStream is, TransportListener listener )
        throws Exception
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

    private void failIfClosed( TransportTask task )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute task " + task );
        }
    }

    public void close()
    {
        closed.set( true );
    }

}
