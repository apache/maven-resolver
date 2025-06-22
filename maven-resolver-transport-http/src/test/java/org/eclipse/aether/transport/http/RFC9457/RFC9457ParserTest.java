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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RFC9457ParserTest {

    @Test
    public void testParseAllFields() {
        String data =
                "{\"type\":\"https://example.com/error\",\"status\":400,\"title\":\"Bad Request\",\"detail\":\"The request could not be understood by the server due to malformed syntax.\",\"instance\":\"https://example.com/error/400\"}";
        RFC9457Payload payload = RFC9457Parser.parse(data);

        assertEquals("https://example.com/error", payload.getType().toString());
        assertEquals(400, payload.getStatus().intValue());
        assertEquals("Bad Request", payload.getTitle());
        assertEquals("The request could not be understood by the server due to malformed syntax.", payload.getDetail());
        assertEquals("https://example.com/error/400", payload.getInstance().toString());
    }

    @Test
    public void testParseWithMissingFields() {
        String data = "{\"type\":\"https://example.com/other_error\",\"status\":403}";
        RFC9457Payload payload = RFC9457Parser.parse(data);

        assertEquals("https://example.com/other_error", payload.getType().toString());
        assertEquals(403, payload.getStatus().intValue());
        assertNull(payload.getTitle());
        assertNull(payload.getDetail());
        assertNull(payload.getInstance());
    }

    @Test
    public void testParseWithNoFields() {
        String data = "{}";
        RFC9457Payload payload = RFC9457Parser.parse(data);

        assertEquals("about:blank", payload.getType().toString());
        assertNull(payload.getStatus());
        assertNull(payload.getTitle());
        assertNull(payload.getDetail());
        assertNull(payload.getInstance());
    }

    @Test
    public void testParseWithNullFields() {
        String data = "{\"type\":null,\"status\":null,\"title\":null,\"detail\":null,\"instance\":null}";
        RFC9457Payload payload = RFC9457Parser.parse(data);

        assertEquals("about:blank", payload.getType().toString());
        assertNull(payload.getStatus());
        assertNull(payload.getTitle());
        assertNull(payload.getDetail());
        assertNull(payload.getInstance());
    }
}
