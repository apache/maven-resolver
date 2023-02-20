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
package org.eclipse.aether.transfer;

import java.nio.ByteBuffer;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 */
public class TransferEventTest {

    private static TransferResource res = new TransferResource("none", "file://nil", "void", null, null);

    private static RepositorySystemSession session = new DefaultRepositorySystemSession();

    @Test
    public void testByteArrayConversion() {
        byte[] buffer = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int length = buffer.length - 2;
        int offset = 1;

        TransferEvent event = new TransferEvent.Builder(session, res)
                .setDataBuffer(buffer, offset, length)
                .build();

        ByteBuffer bb = event.getDataBuffer();
        byte[] dst = new byte[bb.remaining()];
        bb.get(dst);

        byte[] expected = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        assertArrayEquals(expected, dst);
    }

    @Test
    public void testRepeatableReadingOfDataBuffer() {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7};
        ByteBuffer buffer = ByteBuffer.wrap(data);

        TransferEvent event =
                new TransferEvent.Builder(session, res).setDataBuffer(buffer).build();

        assertEquals(8, event.getDataLength());

        ByteBuffer eventBuffer = event.getDataBuffer();
        assertNotNull(eventBuffer);
        assertEquals(8, eventBuffer.remaining());

        byte[] eventData = new byte[8];
        eventBuffer.get(eventData);
        assertArrayEquals(data, eventData);
        assertEquals(0, eventBuffer.remaining());
        assertEquals(8, event.getDataLength());

        eventBuffer = event.getDataBuffer();
        assertNotNull(eventBuffer);
        assertEquals(8, eventBuffer.remaining());
        eventBuffer.get(eventData);
        assertArrayEquals(data, eventData);
    }
}
