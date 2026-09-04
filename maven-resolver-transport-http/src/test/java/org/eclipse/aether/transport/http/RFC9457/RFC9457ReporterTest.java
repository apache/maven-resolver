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
package org.eclipse.aether.transport.http.RFC9457;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RFC9457ReporterTest {

    @Test
    public void testReadBodyTruncatesOversizedStream() throws IOException {
        byte[] oversized = new byte[RFC9457Reporter.MAX_BODY_BYTES + 4096];
        Arrays.fill(oversized, (byte) 'a');
        ByteArrayInputStream is = new ByteArrayInputStream(oversized);
        String body = RFC9457Reporter.readBody(is);
        assertEquals(RFC9457Reporter.MAX_BODY_BYTES, body.length());
        assertEquals(4096, is.available());
    }

    @Test
    public void testReadBodyReadsSmallStreamCompletely() throws IOException {
        String payload = "{\"title\":\"gone\"}";
        String body = RFC9457Reporter.readBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        assertEquals(payload, body);
    }
}
