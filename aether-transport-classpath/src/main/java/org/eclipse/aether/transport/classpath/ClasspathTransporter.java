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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A transporter reading from the classpath.
 */
final class ClasspathTransporter
    extends AbstractTransporter
{

    private final String resourceBase;

    private final ClassLoader classLoader;

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
            URI uri = new URI( repository.getUrl() );
            String ssp = uri.getSchemeSpecificPart();
            if ( ssp.startsWith( "/" ) )
            {
                base = uri.getPath();
                if ( base == null )
                {
                    base = "";
                }
                else if ( base.startsWith( "/" ) )
                {
                    base = base.substring( 1 );
                }
            }
            else
            {
                base = ssp;
            }
            if ( base.length() > 0 && !base.endsWith( "/" ) )
            {
                base += '/';
            }
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e );
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

    private URL getResource( TransportTask task )
        throws Exception
    {
        String resource = resourceBase + task.getLocation().getPath();
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

    @Override
    protected void implPeek( PeekTask task )
        throws Exception
    {
        getResource( task );
    }

    @Override
    protected void implGet( GetTask task )
        throws Exception
    {
        URL url = getResource( task );
        URLConnection conn = url.openConnection();
        utilGet( task, conn.getInputStream(), true, conn.getContentLength(), false );
    }

    @Override
    protected void implPut( PutTask task )
        throws Exception
    {
        throw new UnsupportedOperationException( "Uploading to a classpath: repository is not supported" );
    }

    @Override
    protected void implClose()
    {
    }

}
