package org.eclipse.aether.internal.test.util;

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.aether.spi.io.FileProcessor;

/**
 * A simple file processor implementation to help satisfy component requirements during tests.
 */
public class TestFileProcessor
    implements FileProcessor
{

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
                fos.write( data.getBytes( StandardCharsets.UTF_8 ) );
            }

            fos.close();
            fos = null;
        }
        finally
        {
            try
            {
                if ( fos != null )
                {
                    fos.close();
                }
            }
            catch ( final IOException e )
            {
                // Suppressed due to an exception already thrown in the try block.
            }
        }
    }

    public void write( File target, InputStream source )
        throws IOException
    {
        mkdirs( target.getAbsoluteFile().getParentFile() );

        OutputStream fos = null;
        try
        {
            fos = new BufferedOutputStream( new FileOutputStream( target ) );

            copy( fos, source, null );

            fos.close();
            fos = null;
        }
        finally
        {
            try
            {
                if ( fos != null )
                {
                    fos.close();
                }
            }
            catch ( final IOException e )
            {
                // Suppressed due to an exception already thrown in the try block.
            }
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

            fos = new BufferedOutputStream( new FileOutputStream( target ) );

            total = copy( fos, fis, listener );

            fos.close();
            fos = null;

            fis.close();
            fis = null;
        }
        finally
        {
            try
            {
                if ( fos != null )
                {
                    fos.close();
                }
            }
            catch ( final IOException e )
            {
                // Suppressed due to an exception already thrown in the try block.
            }
            finally
            {
                try
                {
                    if ( fis != null )
                    {
                        fis.close();
                    }
                }
                catch ( final IOException e )
                {
                    // Suppressed due to an exception already thrown in the try block.
                }
            }
        }

        return total;
    }

    private long copy( OutputStream os, InputStream is, ProgressListener listener )
        throws IOException
    {
        long total = 0L;

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
                    ( (Buffer) buffer ).limit( bytes );
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
        target.delete();

        if ( !source.renameTo( target ) )
        {
            copy( source, target );

            target.setLastModified( source.lastModified() );

            source.delete();
        }
    }

}
