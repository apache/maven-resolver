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
package org.eclipse.aether.internal.test.util.http;

import java.io.ByteArrayOutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.TransferCancelledException;

public class RecordingTransportListener extends TransportListener {

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

    private long dataOffset;

    private long dataLength;

    private int startedCount;

    private int progressedCount;

    private boolean cancelStart;

    private boolean cancelProgress;

    @Override
    public void transportStarted(long dataOffset, long dataLength) throws TransferCancelledException {
        startedCount++;
        progressedCount = 0;
        this.dataLength = dataLength;
        this.dataOffset = dataOffset;
        baos.reset();
        if (cancelStart) {
            throw new TransferCancelledException();
        }
    }

    @Override
    public void transportProgressed(ByteBuffer data) throws TransferCancelledException {
        progressedCount++;
        baos.write(data.array(), data.arrayOffset() + ((Buffer) data).position(), data.remaining());
        if (cancelProgress) {
            throw new TransferCancelledException();
        }
    }

    public ByteArrayOutputStream getBaos() {
        return baos;
    }

    public long getDataOffset() {
        return dataOffset;
    }

    public long getDataLength() {
        return dataLength;
    }

    public int getStartedCount() {
        return startedCount;
    }

    public int getProgressedCount() {
        return progressedCount;
    }

    public boolean isCancelStart() {
        return cancelStart;
    }

    public boolean isCancelProgress() {
        return cancelProgress;
    }

    public void cancelStart() {
        this.cancelStart = true;
    }

    public void cancelProgress() {
        this.cancelProgress = true;
    }
}
