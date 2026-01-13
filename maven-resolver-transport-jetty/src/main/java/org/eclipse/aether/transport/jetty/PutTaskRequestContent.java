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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.jetty.client.ByteBufferRequestContent;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.RetainableByteBuffer;
import org.eclipse.jetty.io.internal.ByteChannelContentSource;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.ExceptionUtil;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.thread.AutoLock;
import org.eclipse.jetty.util.thread.SerializedInvoker;

/**
 * Heavily inspired by Jetty's {@code org.eclipse.jetty.io.internal.ByteChannelContentSource} but adjusted to deal with
 * {@link ReadableByteChannel}s and to support rewind (to be able to retry the requests).
 * Also Jetty's {@code ByteChannelContentSource} is an internal package so should not be used directly.
 * @see <a href="https://javadoc.jetty.org/jetty-12/org/eclipse/jetty/io/internal/ByteChannelContentSource.html">ByteChannelContentSource</a>
 * @see <a href="https://github.com/jetty/jetty.project/issues/14324">Jetty Issue #14324</a>
 */
public class PutTaskRequestContent extends ByteBufferRequestContent implements Request.Content {

    public static Request.Content from(PutTask putTask) {
        Supplier<ReadableByteChannel> newChannelSupplier;
        if (putTask.getDataPath() != null) {
            newChannelSupplier = () -> {
                try {
                    return Files.newByteChannel(putTask.getDataPath(), StandardOpenOption.READ);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        } else {
            newChannelSupplier = () -> {
                try {
                    return Channels.newChannel(putTask.newInputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
        return new PutTaskRequestContent(null, newChannelSupplier, 0L, putTask.getDataLength());
    }

    private final AutoLock lock = new AutoLock();
    private final SerializedInvoker invoker = new SerializedInvoker(ByteChannelContentSource.class);
    private final ByteBufferPool.Sized byteBufferPool;
    private ReadableByteChannel byteChannel;
    private final long offset;
    private final long length;
    private RetainableByteBuffer buffer;
    private long offsetRemaining;
    private long totalRead;
    private Runnable demandCallback;
    private Content.Chunk terminal;
    /** Only necessary for rewind support when leveraging the input stream. */
    private Supplier<ReadableByteChannel> newByteChannelSupplier;

    /**
     * Create a {@link ByteChannelContentSource} which reads from a {@link ByteChannel}.
     * @param byteBufferPool The {@link org.eclipse.jetty.io.ByteBufferPool.Sized} to use for any internal buffers.
     * @param byteChannel The {@link ByteChannel}s to use as the source.
     */
    protected PutTaskRequestContent(
            ByteBufferPool.Sized byteBufferPool, Supplier<ReadableByteChannel> newByteChannelSupplier) {
        this(byteBufferPool, newByteChannelSupplier, 0L, -1L);
    }

    /**
     * Create a {@link ByteChannelContentSource} which reads from a {@link ByteChannel}.
     * If the {@link ByteChannel} is an instance of {@link SeekableByteChannel} the implementation will use
     * {@link SeekableByteChannel#position(long)} to navigate to the starting offset.
     * @param byteBufferPool The {@link org.eclipse.jetty.io.ByteBufferPool.Sized} to use for any internal buffers.
     * @param byteChannel The {@link ByteChannel}s to use as the source.
     * @param offset the offset byte of the content to start from.
     *               Must be greater than or equal to 0 and less than the content length (if known).
     * @param length the length of the content to make available, -1 for the full length.
     *               If the size of the content is known, the length may be truncated to the content size minus the offset.
     * @throws IndexOutOfBoundsException if the offset or length are out of range.
     * @see TypeUtil#checkOffsetLengthSize(long, long, long)
     */
    protected PutTaskRequestContent(
            ByteBufferPool.Sized byteBufferPool,
            Supplier<ReadableByteChannel> newByteChannelSupplier,
            long offset,
            long length) {
        this.byteBufferPool = Objects.requireNonNullElse(byteBufferPool, ByteBufferPool.SIZED_NON_POOLING);
        this.byteChannel = newByteChannelSupplier.get();
        this.offset = offset;
        this.length = TypeUtil.checkOffsetLengthSize(offset, length, -1L);
        offsetRemaining = offset;
        this.newByteChannelSupplier = newByteChannelSupplier;
    }

    protected ReadableByteChannel open() throws IOException {
        return byteChannel;
    }

    @Override
    public void demand(Runnable demandCallback) {
        try (AutoLock ignored = lock.lock()) {
            if (this.demandCallback != null) {
                throw new IllegalStateException("demand pending");
            }
            this.demandCallback = demandCallback;
        }
        invoker.run(this::invokeDemandCallback);
    }

    private void invokeDemandCallback() {
        Runnable demandCallback;
        try (AutoLock ignored = lock.lock()) {
            demandCallback = this.demandCallback;
            this.demandCallback = null;
        }
        if (demandCallback != null) {
            ExceptionUtil.run(demandCallback, this::fail);
        }
    }

    protected void lockedSetTerminal(Content.Chunk terminal) {
        assert lock.isHeldByCurrentThread();
        if (terminal == null) {
            terminal = Objects.requireNonNull(terminal);
        } else {
            ExceptionUtil.addSuppressedIfNotAssociated(terminal.getFailure(), terminal.getFailure());
        }
        IO.close(byteChannel);
        if (buffer != null) {
            buffer.release();
        }
        buffer = null;
    }

    private void lockedEnsureOpenOrTerminal() {
        assert lock.isHeldByCurrentThread();
        if (terminal == null && (byteChannel == null || !byteChannel.isOpen())) {
            try {
                byteChannel = open();
                if (byteChannel == null || !byteChannel.isOpen()) {
                    lockedSetTerminal(Content.Chunk.from(new ClosedChannelException(), true));
                } else if (byteChannel instanceof SeekableByteChannel) {
                    ((SeekableByteChannel) byteChannel).position(offset);
                    offsetRemaining = 0;
                }
            } catch (IOException e) {
                lockedSetTerminal(Content.Chunk.from(e, true));
            }
        }
    }

    @Override
    public Content.Chunk read() {
        try (AutoLock ignored = lock.lock()) {
            lockedEnsureOpenOrTerminal();

            if (terminal != null) {
                return terminal;
            }

            if (length == 0) {
                lockedSetTerminal(Content.Chunk.EOF);
                return Content.Chunk.EOF;
            }

            if (buffer == null) {
                buffer = byteBufferPool.acquire();
            } else if (buffer.isRetained()) {
                buffer.release();
                buffer = byteBufferPool.acquire();
            }

            try {
                ByteBuffer byteBuffer = buffer.getByteBuffer();
                if (offsetRemaining > 0) {
                    // Discard all bytes read until we reach the staring offset.
                    while (offsetRemaining > 0) {
                        BufferUtil.clearToFill(byteBuffer);
                        byteBuffer.limit((int) Math.min(buffer.capacity(), offsetRemaining));
                        int read = byteChannel.read(byteBuffer);
                        if (read < 0) {
                            lockedSetTerminal(Content.Chunk.EOF);
                            return terminal;
                        }
                        if (read == 0) {
                            return null;
                        }

                        offsetRemaining -= read;
                    }
                }

                BufferUtil.clearToFill(byteBuffer);
                if (length > 0) {
                    byteBuffer.limit((int) Math.min(buffer.capacity(), length - totalRead));
                }
                int read = byteChannel.read(byteBuffer);
                BufferUtil.flipToFlush(byteBuffer, 0);
                if (read == 0) {
                    return null;
                }
                if (read > 0) {
                    totalRead += read;
                    buffer.retain();
                    if (length < 0 || totalRead < length) {
                        return Content.Chunk.asChunk(byteBuffer, false, buffer);
                    }

                    Content.Chunk last = Content.Chunk.asChunk(byteBuffer, true, buffer);
                    lockedSetTerminal(Content.Chunk.EOF);
                    return last;
                }
                lockedSetTerminal(Content.Chunk.EOF);
            } catch (Throwable t) {
                lockedSetTerminal(Content.Chunk.from(t, true));
            }
        }
        return terminal;
    }

    @Override
    public void fail(Throwable failure) {
        try (AutoLock ignored = lock.lock()) {
            lockedSetTerminal(Content.Chunk.from(failure, true));
        }
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public boolean rewind() {
        try (AutoLock ignored = lock.lock()) {
            // open a new ByteChannel if we don't have a SeekableByteChannel.
            if (!(byteChannel instanceof SeekableByteChannel)) {
                try {
                    byteChannel.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                byteChannel = newByteChannelSupplier.get();
                offsetRemaining = 0;
                totalRead = 0;
                return true;
            }

            // We can remove terminal condition for a rewind that is likely to occur
            if (terminal != null
                    && !Content.Chunk.isFailure(terminal)
                    && (byteChannel == null || byteChannel instanceof SeekableByteChannel)) {
                terminal = null;
            }

            lockedEnsureOpenOrTerminal();
            if (terminal != null || byteChannel == null || !byteChannel.isOpen()) {
                return false;
            }

            try {
                ((SeekableByteChannel) byteChannel).position(offset);
                offsetRemaining = 0;
                totalRead = 0;
                return true;
            } catch (Throwable t) {
                lockedSetTerminal(Content.Chunk.from(t, true));
            }

            return true;
        }
    }
}
