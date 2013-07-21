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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A transporter using {@link java.io.File}.
 */
final class FileTransporter
    extends AbstractTransporter
{

    private final Logger logger;

    private final File basedir;

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

    File getBasedir()
    {
        return basedir;
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
        getFile( task, true );
    }

    @Override
    protected void implGet( GetTask task )
        throws Exception
    {
        File file = getFile( task, true );
        utilGet( task, new FileInputStream( file ), true, file.length(), false );
    }

    @Override
    protected void implPut( PutTask task )
        throws Exception
    {
        File file = getFile( task, false );
        file.getParentFile().mkdirs();
        try
        {
            utilPut( task, new FileOutputStream( file ), true );
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

    @Override
    protected void implClose()
    {
    }

}
