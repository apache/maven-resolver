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
package org.eclipse.aether.internal.test.impl;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.aether.spi.io.FileProcessor;

/**
 */
public class TestFileProcessor
    implements FileProcessor
{

    public static final FileProcessor INSTANCE = new TestFileProcessor();

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

        File canonDir = null;
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

    public void write( File file, String data )
        throws IOException
    {
        mkdirs( file.getParentFile() );

        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream( file );

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

    public void copy( File source, File target )
        throws IOException
    {
        copy( source, target, null );
    }

    public long copy( File source, File target, ProgressListener listener )
        throws IOException
    {
        long total = 0;

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try
        {
            fis = new FileInputStream( source );

            mkdirs( target.getParentFile() );

            fos = new FileOutputStream( target );

            ByteBuffer buffer = ByteBuffer.allocate( 1024 * 32 );
            byte[] array = buffer.array();

            while ( true )
            {
                int bytes = fis.read( array );
                if ( bytes < 0 )
                {
                    break;
                }

                fos.write( array, 0, bytes );

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

    public void move( File source, File target )
        throws IOException
    {
        target.delete();

        if ( !source.renameTo( target ) )
        {
            copy( source, target );

            target.setLastModified( source.lastModified() );

            source.delete();
        }
    }

}
