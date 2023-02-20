/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.spi.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A utility component to perform file-based operations.
 */
public interface FileProcessor {

    /**
     * Creates the directory named by the given abstract pathname, including any necessary but nonexistent parent
     * directories. Note that if this operation fails it may have succeeded in creating some of the necessary parent
     * directories.
     *
     * @param directory The directory to create, may be {@code null}.
     * @return {@code true} if and only if the directory was created, along with all necessary parent directories;
     *         {@code false} otherwise
     */
    boolean mkdirs(File directory);

    /**
     * Writes the given data to a file. UTF-8 is assumed as encoding for the data. Creates the necessary directories for
     * the target file. In case of an error, the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param data The data to write, may be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void write(File target, String data) throws IOException;

    /**
     * Writes the given stream to a file. Creates the necessary directories for the target file. In case of an error,
     * the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param source The stream to write to the file, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void write(File target, InputStream source) throws IOException;

    /**
     * Moves the specified source file to the given target file. If the target file already exists, it is overwritten.
     * Creates the necessary directories for the target file. In case of an error, the created directories will be left
     * on the file system.
     *
     * @param source The file to move from, must not be {@code null}.
     * @param target The file to move to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void move(File source, File target) throws IOException;

    /**
     * Copies the specified source file to the given target file. Creates the necessary directories for the target file.
     * In case of an error, the created directories will be left on the file system.
     *
     * @param source The file to copy from, must not be {@code null}.
     * @param target The file to copy to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void copy(File source, File target) throws IOException;

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
    long copy(File source, File target, ProgressListener listener) throws IOException;

    /**
     * A listener object that is notified for every progress made while copying files.
     *
     * @see FileProcessor#copy(File, File, ProgressListener)
     */
    interface ProgressListener {

        void progressed(ByteBuffer buffer) throws IOException;
    }

    /**
     * Reads checksum from specified file.
     *
     * @throws IOException in case of any IO error.
     * @since 1.8.0
     */
    String readChecksum(File checksumFile) throws IOException;

    /**
     * Writes checksum to specified file.
     *
     * @throws IOException in case of any IO error.
     * @since 1.8.0
     */
    void writeChecksum(File checksumFile, String checksum) throws IOException;
}
