/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.spi.io.FileProcessor;

/**
 * A utility class helping with file-based operations.
 */
@Named
@Component( role = FileProcessor.class )
public class DefaultFileProcessor
    implements FileProcessor
{

    private static void close( Closeable closeable )
    {
        if ( closeable != null )
        {
            try
            {
                closeable.close();
            }
            catch ( IOException e )
            {
                // too bad but who cares
            }
        }
    }

    /**
     * Thread-safe variant of {@link File#mkdirs()}. Adapted from Java 6. Creates the directory named by the given
     * abstract pathname, including any necessary but nonexistent parent directories. Note that if this operation fails
     * it may have succeeded in creating some of the necessary parent directories.
     * 
     * @param directory The directory to create, may be {@code null}.
     * @return {@code true} if and only if the directory was created, along with all necessary parent directories;
     *         {@code false} otherwise
     */
    public boolean mkdirs( File directory )
    {
        if ( directory == null )
        {
            return false;
        }

        if ( directory.exists() )
        {
            return false;
        }
        if ( directory.mkdir() )
        {
            return true;
        }

        File canonDir;
        try
        {
            canonDir = directory.getCanonicalFile();
        }
        catch ( IOException e )
        {
            return false;
        }

        File parentDir = canonDir.getParentFile();
        return ( parentDir != null && ( mkdirs( parentDir ) || parentDir.exists() ) && canonDir.mkdir() );
    }

    public void write( File target, String data )
        throws IOException
    {
        mkdirs( target.getAbsoluteFile().getParentFile() );

        OutputStream fos = null;
        try
        {
            fos = new FileOutputStream( target );

            if ( data != null )
            {
                fos.write( data.getBytes( "UTF-8" ) );
            }

            // allow output to report any flush/close errors
            fos.close();
        }
        finally
        {
            close( fos );
        }
    }

    public void write( File target, InputStream source )
        throws IOException
    {
        mkdirs( target.getAbsoluteFile().getParentFile() );

        OutputStream fos = null;
        try
        {
            fos = new FileOutputStream( target );

            copy( fos, source, null );

            // allow output to report any flush/close errors
            fos.close();
        }
        finally
        {
            close( fos );
        }
    }

    public void copy( File source, File target )
        throws IOException
    {
        copy( source, target, null );
    }

    public long copy( File source, File target, ProgressListener listener )
        throws IOException
    {
        long total = 0;

        InputStream fis = null;
        OutputStream fos = null;
        try
        {
            fis = new FileInputStream( source );

            mkdirs( target.getAbsoluteFile().getParentFile() );

            fos = new FileOutputStream( target );

            total = copy( fos, fis, listener );

            // allow output to report any flush/close errors
            fos.close();
        }
        finally
        {
            close( fis );
            close( fos );
        }

        return total;
    }

    private long copy( OutputStream os, InputStream is, ProgressListener listener )
        throws IOException
    {
        long total = 0;

        ByteBuffer buffer = ByteBuffer.allocate( 1024 * 32 );
        byte[] array = buffer.array();

        while ( true )
        {
            int bytes = is.read( array );
            if ( bytes < 0 )
            {
                break;
            }

            os.write( array, 0, bytes );

            total += bytes;

            if ( listener != null && bytes > 0 )
            {
                try
                {
                    buffer.rewind();
                    buffer.limit( bytes );
                    listener.progressed( buffer );
                }
                catch ( Exception e )
                {
                    // too bad
                }
            }
        }

        return total;
    }

    public void move( File source, File target )
        throws IOException
    {
        if ( !source.renameTo( target ) )
        {
            copy( source, target );

            target.setLastModified( source.lastModified() );

            source.delete();
        }
    }

}
