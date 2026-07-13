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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

import org.eclipse.aether.transfer.TransferCancelledException;

/**
 * An {@code InputStream} wrapper that notifies a {@link TransportListener} about progress when data is read.
 * It throws {@link InterruptedIOException} with a cause of {@link TransferCancelledException} when the transfer is cancelled in the transport listener.
 * The start notification is sent lazily on the first read.
 */
public class TransportListenerNotifyingInputStream extends FilterInputStream {

    private final TransportListener transportListener;
    private final long size;
    private boolean isStarted = false;

    public TransportListenerNotifyingInputStream(InputStream in, TransportListener transportListener, long size) {
        super(in);
        this.transportListener = transportListener;
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        int byteRead = super.read();
        if (byteRead != -1) {
            if (!isStarted) {
                notifyStarted();
            }
            notifyProgress(new byte[] {(byte) byteRead}, 0, 1);
        }
        return byteRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int numBytesRead = super.read(b);
        if (numBytesRead != -1) {
            if (!isStarted) {
                notifyStarted();
            }
            notifyProgress(b, 0, numBytesRead);
        }
        return numBytesRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int numBytesRead = super.read(b, off, len);
        if (numBytesRead != -1) {
            if (!isStarted) {
                notifyStarted();
            }
            notifyProgress(b, off, numBytesRead);
        }
        return numBytesRead;
    }

    private void notifyProgress(byte[] buffer, int offset, int numBytesRead) throws IOException {
        try {
            transportListener.transportProgressed(ByteBuffer.wrap(buffer, offset, numBytesRead));
        } catch (TransferCancelledException e) {
            throw (IOException) new InterruptedIOException().initCause(e);
        }
    }

    private void notifyStarted() throws IOException {
        try {
            transportListener.transportStarted(0, size);
        } catch (TransferCancelledException e) {
            throw (IOException) new InterruptedIOException().initCause(e);
        }
        isStarted = true;
    }
}
