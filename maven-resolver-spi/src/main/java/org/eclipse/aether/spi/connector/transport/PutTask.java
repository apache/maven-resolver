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
package org.eclipse.aether.spi.connector.transport;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A task to upload a resource to the remote repository.
 *
 * @see Transporter#put(PutTask)
 */
public final class PutTask extends TransportTask {

    private File dataFile;

    private byte[] dataBytes = EMPTY;

    /**
     * Creates a new task for the specified remote resource.
     *
     * @param location The relative location of the resource in the remote repository, must not be {@code null}.
     */
    public PutTask(URI location) {
        setLocation(location);
    }

    /**
     * Opens an input stream for the data to be uploaded. The length of the stream can be queried via
     * {@link #getDataLength()}. It's the responsibility of the caller to close the provided stream.
     *
     * @return The input stream for the data, never {@code null}. The stream is unbuffered.
     * @throws IOException If the stream could not be opened.
     */
    public InputStream newInputStream() throws IOException {
        if (dataFile != null) {
            return Files.newInputStream(dataFile.toPath());
        }
        return new ByteArrayInputStream(dataBytes);
    }

    /**
     * Gets the total number of bytes to be uploaded.
     *
     * @return The total number of bytes to be uploaded.
     */
    public long getDataLength() {
        if (dataFile != null) {
            return dataFile.length();
        }
        return dataBytes.length;
    }

    /**
     * Gets the file (if any) with the data to be uploaded.
     *
     * @return The data file or {@code null} if the data resides in memory.
     */
    public File getDataFile() {
        return dataFile;
    }

    /**
     * Sets the file with the data to be uploaded. To upload some data residing already in memory, use
     * {@link #setDataString(String)} or {@link #setDataBytes(byte[])}.
     *
     * @param dataFile The data file, may be {@code null} if the resource data is provided directly from memory.
     * @return This task for chaining, never {@code null}.
     */
    public PutTask setDataFile(File dataFile) {
        this.dataFile = dataFile;
        dataBytes = EMPTY;
        return this;
    }

    /**
     * Sets the binary data to be uploaded.
     *
     * @param bytes The binary data, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public PutTask setDataBytes(byte[] bytes) {
        this.dataBytes = (bytes != null) ? bytes : EMPTY;
        dataFile = null;
        return this;
    }

    /**
     * Sets the textual data to be uploaded. The text is encoded using UTF-8 before transmission.
     *
     * @param str The textual data, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public PutTask setDataString(String str) {
        return setDataBytes((str != null) ? str.getBytes(StandardCharsets.UTF_8) : null);
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     *
     * @param listener The listener to notify of progress, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public PutTask setListener(TransportListener listener) {
        super.setListener(listener);
        return this;
    }

    @Override
    public String toString() {
        return ">> " + getLocation();
    }
}
