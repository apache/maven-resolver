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
package org.eclipse.aether.internal.test.util;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import org.junit.Assert;

public class TestFileUtils
{

    private static final File TMP = new File( System.getProperty( "java.io.tmpdir" ), "aether-"
        + UUID.randomUUID().toString().substring( 0, 8 ) );

    static
    {
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    delete( TMP );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        } );
    }

    public static void deleteTempFiles()
        throws IOException
    {
        delete( TMP );
    }

    public static File createTempFile( String contents )
        throws IOException
    {
        return createTempFile( contents.getBytes( "UTF-8" ), 1 );
    }

    public static File createTempFile( byte[] pattern, int repeat )
        throws IOException
    {
        mkdirs( TMP );
        File tmpFile = File.createTempFile( "tmpfile-", ".data", TMP );
        write( pattern, repeat, tmpFile );

        return tmpFile;
    }

    public static void write( String content, File file )
        throws IOException
    {
        try
        {
            write( content.getBytes( "UTF-8" ), 1, file );
        }
        catch ( UnsupportedEncodingException e )
        {
            // broken VM
            throw new IOException( e.getMessage() );
        }
    }

    public static void write( byte[] pattern, int repeat, File file )
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
        }
        finally
        {
            close( out );
        }
    }

    public static long copy( File source, File target )
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

            for ( byte[] buffer = new byte[1024 * 32];; )
            {
                int bytes = fis.read( buffer );
                if ( bytes < 0 )
                {
                    break;
                }

                fos.write( buffer, 0, bytes );

                total += bytes;
            }
        }
        finally
        {
            close( fis );
            close( fos );
        }

        return total;
    }

    private static void close( Closeable c )
        throws IOException
    {
        if ( c != null )
        {
            try
            {
                c.close();
            }
            catch ( IOException e )
            {
                // ignore
            }
        }
    }

    public static void delete( File file )
        throws IOException
    {
        if ( file == null )
        {
            return;
        }

        Collection<File> undeletables = new ArrayList<File>();

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

    public static byte[] getContent( File file )
        throws IOException
    {
        RandomAccessFile in = null;
        try
        {
            in = new RandomAccessFile( file, "r" );
            byte[] actual = new byte[(int) in.length()];
            in.readFully( actual );
            return actual;
        }
        finally
        {
            close( in );
        }
    }

    public static void assertContent( byte[] expected, File file )
        throws IOException
    {
        Assert.assertArrayEquals( expected, getContent( file ) );
    }

    public static void assertContent( String expected, File file )
        throws IOException
    {
        byte[] content = getContent( file );
        String msg = new String( content, "UTF-8" );
        if ( msg.length() > 10 )
        {
            msg = msg.substring( 0, 10 ) + "...";
        }
        Assert.assertArrayEquals( "content was '" + msg + "'\n", expected.getBytes( "UTF-8" ), content );
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

        delete( tmpFile );
        mkdirs( tmpFile );

        return tmpFile;
    }

    public static void read( Properties props, File file )
        throws IOException
    {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream( file );
            props.load( fis );
        }
        finally
        {
            close( fis );
        }
    }

    public static void write( Properties props, File file )
        throws IOException
    {
        file.getParentFile().mkdirs();

        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream( file );
            props.store( fos, "aether-test" );
        }
        finally
        {
            close( fos );
        }
    }

}
