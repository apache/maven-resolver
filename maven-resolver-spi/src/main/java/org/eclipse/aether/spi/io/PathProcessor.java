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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * A utility component to perform file-based operations.
 *
 * @since 2.0.0
 */
public interface PathProcessor {
    /**
     * Returns last modified of path in milliseconds, if exists.
     *
     * @param path The path, may be {@code null}.
     * @throws UncheckedIOException If an I/O error occurs.
     */
    default long lastModified(Path path, long defValue) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (NoSuchFileException e) {
            return defValue;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sets last modified of path in milliseconds, if exists.
     *
     * @param path The path, may be {@code null}.
     * @throws IOException If an I/O error occurs. Some exceptions/reasons of failure to set mtime may be swallowed,
     * and can be multiple, ranging from "file not found" to cases when FS does not support the setting the mtime.
     * @since 2.0.0
     */
    boolean setLastModified(Path path, long value) throws IOException;

    /**
     * Returns size of file, if exists.
     *
     * @param path The path, may be {@code null}.
     * @throws UncheckedIOException If an I/O error occurs.
     */
    default long size(Path path, long defValue) {
        try {
            return Files.size(path);
        } catch (NoSuchFileException e) {
            return defValue;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the given data to a file. UTF-8 is assumed as encoding for the data. Creates the necessary directories for
     * the target file. In case of an error, the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param data The data to write, may be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void write(Path target, String data) throws IOException;

    /**
     * Writes the given stream to a file. Creates the necessary directories for the target file. In case of an error,
     * the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param source The stream to write to the file, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void write(Path target, InputStream source) throws IOException;

    /**
     * Writes the given data to a file. UTF-8 is assumed as encoding for the data. Creates the necessary directories for
     * the target file. In case of an error, the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param data The data to write, may be {@code null}.
     * @throws IOException If an I/O error occurs.
     * @since 2.0.13
     */
    void writeWithBackup(Path target, String data) throws IOException;

    /**
     * Writes the given stream to a file. Creates the necessary directories for the target file. In case of an error,
     * the created directories will be left on the file system.
     *
     * @param target The file to write to, must not be {@code null}. This file will be overwritten.
     * @param source The stream to write to the file, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     * @since 2.0.13
     */
    void writeWithBackup(Path target, InputStream source) throws IOException;

    /**
     * Moves the specified source file to the given target file. If the target file already exists, it is overwritten.
     * Creates the necessary directories for the target file. In case of an error, the created directories will be left
     * on the file system.
     *
     * @param source The file to move from, must not be {@code null}.
     * @param target The file to move to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    void move(Path source, Path target) throws IOException;

    /**
     * Copies the specified source file to the given target file. Creates the necessary directories for the target file.
     * In case of an error, the created directories will be left on the file system.
     *
     * @param source The file to copy from, must not be {@code null}.
     * @param target The file to copy to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     */
    default void copy(Path source, Path target) throws IOException {
        copy(source, target, null);
    }

    /**
     * Same as {@link #copy(Path, Path)} but sets source file last modified timestamp on target as well.
     *
     * @param source The file to copy from, must not be {@code null}.
     * @param target The file to copy to, must not be {@code null}.
     * @throws IOException If an I/O error occurs.
     * @see #setLastModified(Path, long)
     * @since 2.0.0
     */
    default void copyWithTimestamp(Path source, Path target) throws IOException {
        copy(source, target, null);
        setLastModified(target, Files.getLastModifiedTime(source).toMillis());
    }

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
    long copy(Path source, Path target, ProgressListener listener) throws IOException;

    /**
     * A listener object that is notified for every progress made while copying files.
     *
     * @see PathProcessor#copy(Path, Path, ProgressListener)
     */
    interface ProgressListener {
        void progressed(ByteBuffer buffer) throws IOException;
    }

    // Temporary files

    /**
     * A temporary file, that is removed when closed.
     *
     * @since 2.0.13
     */
    interface TempFile extends Closeable {
        /**
         * Returns the path of the created temp file.
         */
        Path getPath();
    }

    /**
     * A collocated temporary file, that resides next to a "target" file, and is removed when closed.
     *
     * @since 2.0.13
     */
    interface CollocatedTempFile extends TempFile {
        /**
         * Upon close, atomically moves temp file to target file it is collocated with overwriting target (if exists).
         * Invocation of this method merely signals that caller ultimately wants temp file to replace the target
         * file, but when this method returns, the move operation did not yet happen, it will happen when this
         * instance is closed.
         * <p>
         * Invoking this method <em>without writing to temp file</em> {@link #getPath()} (thus, not creating a temp
         * file to be moved) is considered a bug, a mistake of the caller. Caller of this method should ensure
         * that this method is invoked ONLY when the temp file is created and moving it to its final place is
         * required.
         */
        void move() throws IOException;
    }

    /**
     * Creates a {@link TempFile} instance and backing temporary file on file system. It will be located in the default
     * temporary-file directory. Returned instance should be handled in try-with-resource construct and created
     * temp file is removed (if exists) when returned instance is closed.
     * <p>
     * This method uses {@link Files#createTempFile(String, String, java.nio.file.attribute.FileAttribute[])} to create
     * the temporary file on file system.
     *
     * @since 2.0.13
     */
    TempFile newTempFile() throws IOException;

    /**
     * Creates a {@link CollocatedTempFile} instance for given file without backing file. The path will be located in
     * same directory where given file is, and will reuse its name for generated (randomized) name. Returned instance
     * should be handled in try-with-resource and created temp path is removed (if exists) when returned instance is
     * closed. The {@link CollocatedTempFile#move()} makes possible to atomically replace passed in file with the
     * processed content written into a file backing the {@link CollocatedTempFile} instance.
     * <p>
     * The {@code file} nor it's parent directories have to exist. The parent directories are created if needed.
     * <p>
     * This method uses {@link Path#resolve(String)} to create the temporary file path in passed in file parent
     * directory, but it does NOT create backing file on file system.
     *
     * @since 2.0.13
     */
    CollocatedTempFile newTempFile(Path file) throws IOException;
}
