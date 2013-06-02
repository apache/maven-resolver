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
package org.eclipse.aether.spi.connector.transport;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;

/**
 * A task to download a resource from the remote repository.
 * 
 * @see Transporter#get(GetTask)
 */
public final class GetTask
    extends TransportTask
{

    private File dataFile;

    private boolean resume;

    private ByteArrayOutputStream dataBytes;

    /**
     * Creates a new task for the specified remote resource.
     * 
     * @param location The relative location of the resource in the remote repository, must not be {@code null}.
     */
    public GetTask( URI location )
    {
        setLocation( location );
    }

    /**
     * Opens an output stream to store the downloaded data. Depending on {@link #getDataFile()}, this stream writes
     * either to a file on disk or a growable buffer in memory. It's the responsibility of the caller to close the
     * provided stream.
     * 
     * @return The output stream for the data, never {@code null}.
     * @throws IOException If the stream could not be opened.
     */
    public OutputStream newOutputStream()
        throws IOException
    {
        return newOutputStream( false );
    }

    /**
     * Opens an output stream to store the downloaded data. Depending on {@link #getDataFile()}, this stream writes
     * either to a file on disk or a growable buffer in memory. It's the responsibility of the caller to close the
     * provided stream.
     * 
     * @param resume {@code true} if the download resumes from the byte offset given by {@link #getResumeOffset()},
     *            {@code false} if the download starts at the first byte of the resource.
     * @return The output stream for the data, never {@code null}.
     * @throws IOException If the stream could not be opened.
     */
    public OutputStream newOutputStream( boolean resume )
        throws IOException
    {
        if ( dataFile != null )
        {
            return new BufferedOutputStream( new FileOutputStream( dataFile, this.resume && resume ) );
        }
        if ( dataBytes == null )
        {
            dataBytes = new ByteArrayOutputStream( 1024 );
        }
        else if ( !resume )
        {
            dataBytes.reset();
        }
        return dataBytes;
    }

    /**
     * Gets the file (if any) where the downloaded data should be stored. If the specified file already exists, it will
     * be overwritten.
     * 
     * @return The data file or {@code null} if the data will be buffered in memory.
     */
    public File getDataFile()
    {
        return dataFile;
    }

    /**
     * Sets the file where the downloaded data should be stored. If the specified file already exists, it will be
     * overwritten. Unless the caller can reasonably expect the resource to be small, use of a data file is strongly
     * recommended to avoid exhausting heap memory during the download.
     * 
     * @param dataFile The file to store the downloaded data, may be {@code null} to store the data in memory.
     * @return This task for chaining, never {@code null}.
     */
    public GetTask setDataFile( File dataFile )
    {
        return setDataFile( dataFile, false );
    }

    /**
     * Sets the file where the downloaded data should be stored. If the specified file already exists, it will be
     * overwritten or appended to, depending on the {@code resume} argument and the capabilities of the transporter.
     * Unless the caller can reasonably expect the resource to be small, use of a data file is strongly recommended to
     * avoid exhausting heap memory during the download.
     * 
     * @param dataFile The file to store the downloaded data, may be {@code null} to store the data in memory.
     * @param resume {@code true} to request resuming a previous download attempt, starting from the current length of
     *            the data file, {@code false} to download the resource from its beginning.
     * @return This task for chaining, never {@code null}.
     */
    public GetTask setDataFile( File dataFile, boolean resume )
    {
        this.dataFile = dataFile;
        this.resume = resume;
        return this;
    }

    /**
     * Gets the byte offset within the resource from which the download should resume if supported.
     * 
     * @return The zero-based index of the first byte to download or {@code 0} for a full download from the start of the
     *         resource, never negative.
     */
    public long getResumeOffset()
    {
        if ( resume )
        {
            if ( dataFile != null )
            {
                return dataFile.length();
            }
            if ( dataBytes != null )
            {
                return dataBytes.size();
            }
        }
        return 0;
    }

    /**
     * Gets the data that was downloaded into memory. <strong>Note:</strong> This method may only be called if
     * {@link #getDataFile()} is {@code null} as otherwise the downloaded data has been written directly to disk.
     * 
     * @return The possibly empty data bytes, never {@code null}.
     */
    public byte[] getDataBytes()
    {
        if ( dataFile != null || dataBytes == null )
        {
            return EMPTY;
        }
        return dataBytes.toByteArray();
    }

    /**
     * Gets the data that was downloaded into memory as a string. The downloaded data is assumed to be encoded using
     * UTF-8. <strong>Note:</strong> This method may only be called if {@link #getDataFile()} is {@code null} as
     * otherwise the downloaded data has been written directly to disk.
     * 
     * @return The possibly empty data string, never {@code null}.
     */
    public String getDataString()
    {
        if ( dataFile != null || dataBytes == null )
        {
            return "";
        }
        try
        {
            return dataBytes.toString( "UTF-8" );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new IllegalStateException( e );
        }
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     * 
     * @param listener The listener to notify of progress, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public GetTask setListener( TransportListener listener )
    {
        super.setListener( listener );
        return this;
    }

    @Override
    public String toString()
    {
        return "<< " + getLocation();
    }

}
