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
package org.eclipse.aether.transport.jetty;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.jetty.client.util.AbstractRequestContent;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;

class PutTaskRequestContent extends AbstractRequestContent {
    private final PutTask putTask;
    private final int bufferSize;
    private ByteBufferPool bufferPool;
    private boolean useDirectByteBuffers = true;

    @SuppressWarnings("checkstyle:MagicNumber")
    PutTaskRequestContent(PutTask putTask) {
        this(putTask, 4096);
    }

    PutTaskRequestContent(PutTask putTask, int bufferSize) {
        super("application/octet-stream");
        this.putTask = putTask;
        this.bufferSize = bufferSize;
    }

    @Override
    public long getLength() {
        return putTask.getDataLength();
    }

    @Override
    public boolean isReproducible() {
        return true;
    }

    public ByteBufferPool getByteBufferPool() {
        return bufferPool;
    }

    public void setByteBufferPool(ByteBufferPool byteBufferPool) {
        this.bufferPool = byteBufferPool;
    }

    public boolean isUseDirectByteBuffers() {
        return useDirectByteBuffers;
    }

    public void setUseDirectByteBuffers(boolean useDirectByteBuffers) {
        this.useDirectByteBuffers = useDirectByteBuffers;
    }

    @Override
    protected Subscription newSubscription(Consumer consumer, boolean emitInitialContent) {
        return new SubscriptionImpl(consumer, emitInitialContent);
    }

    private class SubscriptionImpl extends AbstractSubscription {
        private ReadableByteChannel channel;
        private long readTotal;

        private SubscriptionImpl(Consumer consumer, boolean emitInitialContent) {
            super(consumer, emitInitialContent);
        }

        @Override
        protected boolean produceContent(Producer producer) throws IOException {
            ByteBuffer buffer;
            boolean last;
            if (channel == null) {
                if (putTask.getDataFile() != null) {
                    channel = Files.newByteChannel(putTask.getDataFile().toPath(), StandardOpenOption.READ);
                } else {
                    channel = Channels.newChannel(putTask.newInputStream());
                }
            }

            buffer = bufferPool == null
                    ? BufferUtil.allocate(bufferSize, isUseDirectByteBuffers())
                    : bufferPool.acquire(bufferSize, isUseDirectByteBuffers());

            BufferUtil.clearToFill(buffer);
            int read = channel.read(buffer);
            BufferUtil.flipToFlush(buffer, 0);
            if (!channel.isOpen() && read < 0) {
                throw new EOFException("EOF reached for " + putTask);
            }
            if (read > 0) {
                readTotal += read;
            }
            last = readTotal == getLength();
            if (last) {
                IO.close(channel);
            }
            return producer.produce(buffer, last, Callback.from(() -> release(buffer)));
        }

        private void release(ByteBuffer buffer) {
            if (bufferPool != null) {
                bufferPool.release(buffer);
            }
        }

        @Override
        public void fail(Throwable failure) {
            super.fail(failure);
            IO.close(channel);
        }
    }
}
