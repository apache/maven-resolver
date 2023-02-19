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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A task to download a resource from the remote repository.
 *
 * @see Transporter#get(GetTask)
 */
public final class GetTask extends TransportTask {

    private File dataFile;

    private boolean resume;

    private ByteArrayOutputStream dataBytes;

    private Map<String, String> checksums;

    /**
     * Creates a new task for the specified remote resource.
     *
     * @param location The relative location of the resource in the remote repository, must not be {@code null}.
     */
    public GetTask(URI location) {
        checksums = Collections.emptyMap();
        setLocation(location);
    }

    /**
     * Opens an output stream to store the downloaded data. Depending on {@link #getDataFile()}, this stream writes
     * either to a file on disk or a growable buffer in memory. It's the responsibility of the caller to close the
     * provided stream.
     *
     * @return The output stream for the data, never {@code null}. The stream is unbuffered.
     * @throws IOException If the stream could not be opened.
     */
    public OutputStream newOutputStream() throws IOException {
        return newOutputStream(false);
    }

    /**
     * Opens an output stream to store the downloaded data. Depending on {@link #getDataFile()}, this stream writes
     * either to a file on disk or a growable buffer in memory. It's the responsibility of the caller to close the
     * provided stream.
     *
     * @param resume {@code true} if the download resumes from the byte offset given by {@link #getResumeOffset()},
     *            {@code false} if the download starts at the first byte of the resource.
     * @return The output stream for the data, never {@code null}. The stream is unbuffered.
     * @throws IOException If the stream could not be opened.
     */
    public OutputStream newOutputStream(boolean resume) throws IOException {
        if (dataFile != null) {
            if (this.resume && resume) {
                return Files.newOutputStream(
                        dataFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } else {
                return Files.newOutputStream(
                        dataFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
        if (dataBytes == null) {
            dataBytes = new ByteArrayOutputStream(1024);
        } else if (!resume) {
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
    public File getDataFile() {
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
    public GetTask setDataFile(File dataFile) {
        return setDataFile(dataFile, false);
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
    public GetTask setDataFile(File dataFile, boolean resume) {
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
    public long getResumeOffset() {
        if (resume) {
            if (dataFile != null) {
                return dataFile.length();
            }
            if (dataBytes != null) {
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
    public byte[] getDataBytes() {
        if (dataFile != null || dataBytes == null) {
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
    public String getDataString() {
        if (dataFile != null || dataBytes == null) {
            return "";
        }
        return new String(dataBytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Sets the listener that is to be notified during the transfer.
     *
     * @param listener The listener to notify of progress, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public GetTask setListener(TransportListener listener) {
        super.setListener(listener);
        return this;
    }

    /**
     * Gets the checksums which the remote repository advertises for the resource. The map is keyed by algorithm name
     * and the values are hexadecimal representations of the corresponding value. <em>Note:</em> This is optional
     * data that a transporter may return if the underlying transport protocol provides metadata (e.g. HTTP headers)
     * along with the actual resource data. Checksums returned by this method have kind of
     * {@link org.eclipse.aether.spi.connector.checksum.ChecksumPolicy.ChecksumKind#REMOTE_INCLUDED}.
     *
     * @return The (read-only) checksums advertised for the downloaded resource, possibly empty but never {@code null}.
     */
    public Map<String, String> getChecksums() {
        return checksums;
    }

    /**
     * Sets a checksum which the remote repository advertises for the resource. <em>Note:</em> Transporters should only
     * use this method to record checksum information which is readily available while performing the actual download,
     * they should not perform additional transfers to gather this data.
     *
     * @param algorithm The name of the checksum algorithm (e.g. {@code "SHA-1"}, may be {@code null}.
     * @param value The hexadecimal representation of the checksum, may be {@code null}.
     * @return This task for chaining, never {@code null}.
     */
    public GetTask setChecksum(String algorithm, String value) {
        if (algorithm != null) {
            if (checksums.isEmpty()) {
                checksums = new HashMap<>();
            }
            if (value != null && value.length() > 0) {
                checksums.put(algorithm, value);
            } else {
                checksums.remove(algorithm);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return "<< " + getLocation();
    }
}
