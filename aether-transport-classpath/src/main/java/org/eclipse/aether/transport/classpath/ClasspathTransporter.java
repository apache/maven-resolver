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
package org.eclipse.aether.transport.classpath;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetRequest;
import org.eclipse.aether.spi.connector.transport.NoTransporterException;
import org.eclipse.aether.spi.connector.transport.PeekRequest;
import org.eclipse.aether.spi.connector.transport.PutRequest;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.TransportRequest;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A transporter reading from the classpath.
 */
final class ClasspathTransporter
    implements Transporter
{

    private final AtomicBoolean closed = new AtomicBoolean();

    private final ClassLoader classLoader;

    private final String resourceBase;

    public ClasspathTransporter( RepositorySystemSession session, RemoteRepository repository, Logger logger )
        throws NoTransporterException
    {
        if ( !"classpath".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }
        String base;
        try
        {
            base = new URI( repository.getUrl() ).getPath();
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e );
        }
        if ( base == null )
        {
            base = "";
        }
        else
        {
            if ( base.startsWith( "/" ) )
            {
                base = base.substring( 1 );
            }
            if ( base.length() > 0 && !base.endsWith( "/" ) )
            {
                base += '/';
            }
        }
        resourceBase = base;

        Object cl = ConfigUtils.getObject( session, null, ClasspathTransporterFactory.CONFIG_PROP_CLASS_LOADER );
        if ( cl instanceof ClassLoader )
        {
            classLoader = (ClassLoader) cl;
        }
        else
        {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
    }

    private URL getResource( TransportRequest request )
        throws Exception
    {
        String resource = resourceBase + request.getLocation().getPath();
        URL url = classLoader.getResource( resource );
        if ( url == null )
        {
            throw new ResourceNotFoundException( "Could not locate " + resource );
        }
        return url;
    }

    public int classify( Throwable error )
    {
        if ( error instanceof ResourceNotFoundException )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    private void failIfClosed( TransportRequest request )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute request " + request );
        }
    }

    public void peek( PeekRequest request )
        throws Exception
    {
        failIfClosed( request );

        getResource( request );
    }

    public void get( GetRequest request )
        throws Exception
    {
        failIfClosed( request );

        URL url = getResource( request );
        URLConnection conn = url.openConnection();
        InputStream is = conn.getInputStream();
        try
        {
            request.getListener().transportStarted( 0, conn.getContentLength() );
            OutputStream os = request.newOutputStream();
            try
            {
                copy( os, is, request.getListener() );
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

    public void put( PutRequest request )
        throws Exception
    {
        failIfClosed( request );

        throw new UnsupportedOperationException( "Uploading to a classpath: repository is not supported" );
    }

    public void close()
    {
        closed.set( true );
    }

}
