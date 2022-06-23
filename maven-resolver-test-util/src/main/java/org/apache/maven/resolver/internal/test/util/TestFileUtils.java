package org.apache.maven.resolver.internal.test.util;

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
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

/**
 * Provides utility methods to read and write (temporary) files.
 */
public class TestFileUtils
{

    private static final File TMP = new File( System.getProperty( "java.io.tmpdir" ), "aether-"
        + UUID.randomUUID().toString().substring( 0, 8 ) );

    static
    {
        Runtime.getRuntime().addShutdownHook( new Thread( () ->
        {
            try
            {
                deleteFile( TMP );
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
        } ) );
    }

    private TestFileUtils()
    {
        // hide constructor
    }

    public static void deleteTempFiles()
        throws IOException
    {
        deleteFile( TMP );
    }

    public static void deleteFile( File file )
        throws IOException
    {
        if ( file == null )
        {
            return;
        }

        Collection<File> undeletables = new ArrayList<>();

        delete( file, undeletables );

        if ( !undeletables.isEmpty() )
        {
            throw new IOException( "Failed to delete " + undeletables );
        }
    }

    private static void delete( File file, Collection<File> undeletables )
    {
        String[] children = file.list();
        if ( children != null )
        {
            for ( String child : children )
            {
                delete( new File( file, child ), undeletables );
            }
        }

        if ( !del( file ) )
        {
            undeletables.add( file.getAbsoluteFile() );
        }
    }

    private static boolean del( File file )
    {
        for ( int i = 0; i < 10; i++ )
        {
            if ( file.delete() || !file.exists() )
            {
                return true;
            }
        }
        return false;
    }

    public static boolean mkdirs( File directory )
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

    public static File createTempFile( String contents )
        throws IOException
    {
        return createTempFile( contents.getBytes( StandardCharsets.UTF_8 ), 1 );
    }

    public static File createTempFile( byte[] pattern, int repeat )
        throws IOException
    {
        mkdirs( TMP );
        File tmpFile = File.createTempFile( "tmpfile-", ".data", TMP );
        writeBytes( tmpFile, pattern, repeat );
        return tmpFile;
    }

    public static File createTempDir()
        throws IOException
    {
        return createTempDir( "" );
    }

    public static File createTempDir( String suffix )
        throws IOException
    {
        mkdirs( TMP );
        File tmpFile = File.createTempFile( "tmpdir-", suffix, TMP );
        deleteFile( tmpFile );
        mkdirs( tmpFile );
        return tmpFile;
    }

    public static long copyFile( File source, File target )
        throws IOException
    {
        long total = 0;

        FileInputStream fis = null;
        OutputStream fos = null;
        try
        {
            fis = new FileInputStream( source );

            mkdirs( target.getParentFile() );

            fos = new BufferedOutputStream( new FileOutputStream( target ) );

            for ( byte[] buffer = new byte[ 1024 * 32 ];; )
            {
                int bytes = fis.read( buffer );
                if ( bytes < 0 )
                {
                    break;
                }

                fos.write( buffer, 0, bytes );

                total += bytes;
            }

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

    public static byte[] readBytes( File file )
        throws IOException
    {
        RandomAccessFile in = null;
        try
        {
            in = new RandomAccessFile( file, "r" );
            byte[] actual = new byte[ (int) in.length() ];
            in.readFully( actual );
            in.close();
            in = null;
            return actual;
        }
        finally
        {
            try
            {
                if ( in != null )
                {
                    in.close();
                }
            }
            catch ( final IOException e )
            {
                // Suppressed due to an exception already thrown in the try block.
            }
        }
    }

    public static void writeBytes( File file, byte[] pattern, int repeat )
        throws IOException
    {
        file.deleteOnExit();
        file.getParentFile().mkdirs();
        OutputStream out = null;
        try
        {
            out = new BufferedOutputStream( new FileOutputStream( file ) );
            for ( int i = 0; i < repeat; i++ )
            {
                out.write( pattern );
            }
            out.close();
            out = null;
        }
        finally
        {
            try
            {
                if ( out != null )
                {
                    out.close();
                }
            }
            catch ( final IOException e )
            {
                // Suppressed due to an exception already thrown in the try block.
            }
        }
    }

    public static String readString( File file )
        throws IOException
    {
        byte[] content = readBytes( file );
        return new String( content, StandardCharsets.UTF_8 );
    }

    public static void writeString( File file, String content )
        throws IOException
    {
        writeBytes( file, content.getBytes( StandardCharsets.UTF_8 ), 1 );
    }

    public static void readProps( File file, Properties props )
        throws IOException
    {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream( file );
            props.load( fis );
            fis.close();
            fis = null;
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

    public static void writeProps( File file, Properties props )
        throws IOException
    {
        file.getParentFile().mkdirs();

        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream( file );
            props.store( fos, "aether-test" );
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

}
