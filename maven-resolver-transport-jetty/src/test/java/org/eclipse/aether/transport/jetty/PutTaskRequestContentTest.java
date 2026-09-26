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

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

import org.eclipse.jetty.io.ByteBufferPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PutTaskRequestContentTest {
    @Test
    void readPropagatesErrorsFromChannel() {
        ReadableByteChannel channel = new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer destination) {
                throw new AssertionError("fatal read failure");
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {}
        };
        PutTaskRequestContent content = new PutTaskRequestContent(ByteBufferPool.SIZED_NON_POOLING, () -> channel);

        assertThrows(AssertionError.class, content::read);
    }

    @Test
    void rewindPropagatesErrorsFromSeekableChannel() {
        SeekableByteChannel channel = new SeekableByteChannel() {
            @Override
            public int read(ByteBuffer destination) {
                return -1;
            }

            @Override
            public int write(ByteBuffer source) {
                return 0;
            }

            @Override
            public long position() {
                return 0;
            }

            @Override
            public SeekableByteChannel position(long newPosition) {
                throw new AssertionError("fatal rewind failure");
            }

            @Override
            public long size() {
                return 0;
            }

            @Override
            public SeekableByteChannel truncate(long size) {
                return this;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {}
        };
        PutTaskRequestContent content = new PutTaskRequestContent(ByteBufferPool.SIZED_NON_POOLING, () -> channel);

        assertThrows(AssertionError.class, content::rewind);
    }
}
