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
package org.eclipse.aether.spi.io;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A utility component to perform file-based operations.
 */
public interface FileProcessor
{

    /**
     * Creates the directory named by the given abstract pathname, including any necessary but nonexistent parent
     * directories. Note that if this operation fails it may have succeeded in creating some of the necessary parent
     * directories.
     * 
     * @param directory The directory to create, may be {@code null}.
     * @return {@code true} if and only if the directory was created, along with all necessary parent directories;
     *         {@code false} otherwise
     */
    boolean mkdirs( File directory );

    /**
     * Writes the given data to a file. UTF-8 is assumed as encoding for the data. Creates the necessary directories for
     * the target file. In case of an error, the created directories will be left on the file system.
     * 
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param data The data to write, may be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void write( File target, String data )
        throws IOException;

    /**
     * Moves the specified source file to the given target file. If the target file already exists, it is overwritten.
     * Creates the necessary directories for the target file. In case of an error, the created directories will be left
     * on the file system.
     * 
     * @param source The file to move from, must not be {@code null}.
     * @param target The file to move to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void move( File source, File target )
        throws IOException;

    /**
     * Copies the specified source file to the given target file. Creates the necessary directories for the target file.
     * In case of an error, the created directories will be left on the file system.
     * 
     * @param source The file to copy from, must not be {@code null}.
     * @param target The file to copy to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void copy( File source, File target )
        throws IOException;

    /**
     * Copies the specified source file to the given target file. Creates the necessary directories for the target file.
     * In case of an error, the created directories will be left on the file system.
     * 
     * @param source The file to copy from, must not be {@code null}.
     * @param target The file to copy to, must not be {@code null}.
     * @param listener The listener to notify about the copy progress, may be {@code null}.
     * @return The number of copied bytes.
     * @throws IOException If an I/O error occurs.
     */
    long copy( File source, File target, ProgressListener listener )
        throws IOException;

    /**
     * A listener object that is notified for every progress made while copying files.
     * 
     * @see FileProcessor#copy(File, File, ProgressListener)
     */
    public interface ProgressListener
    {

        void progressed( ByteBuffer buffer )
            throws IOException;

    }

}
