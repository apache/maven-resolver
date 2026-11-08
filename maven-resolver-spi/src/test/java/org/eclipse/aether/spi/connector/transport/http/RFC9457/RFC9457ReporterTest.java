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
package org.eclipse.aether.spi.connector.transport.http.RFC9457;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RFC9457ReporterTest {

    private static final class TestReporter extends RFC9457Reporter<String, IOException, Object> {
        private final boolean rfc9457;

        private TestReporter(boolean rfc9457) {
            this.rfc9457 = rfc9457;
        }

        @Override
        protected boolean isRFC9457Message(String response) {
            return rfc9457;
        }

        @Override
        protected int getStatusCode(String response) {
            return 404;
        }

        @Override
        protected String getReasonPhrase(String response) {
            return "Not Found";
        }

        @Override
        protected String getBody(String response) throws IOException {
            try (InputStream is = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))) {
                return readBody(is);
            }
        }

        @Override
        public void prepareRequest(Object request) {}
    }

    @Test
    void hasRFC9457ContentType() {
        RFC9457Reporter<Object, Exception, Object> reporter = new RFC9457Reporter<Object, Exception, Object>() {
            @Override
            protected boolean isRFC9457Message(Object response) {
                return false;
            }

            @Override
            protected int getStatusCode(Object response) {
                return 0;
            }

            @Override
            protected String getReasonPhrase(Object response) {
                return null;
            }

            @Override
            protected String getBody(Object response) {
                return null;
            }

            @Override
            public void prepareRequest(Object request) {}
        };
        assertTrue(reporter.hasRFC9457ContentType("application/problem+json"));
        assertTrue(reporter.hasRFC9457ContentType("application/problem+json; charset=utf-8"));
    }

    @Test
    void readBodyTruncatesOversizedStream() throws IOException {
        byte[] oversized = new byte[RFC9457Reporter.MAX_BODY_BYTES + 4096];
        Arrays.fill(oversized, (byte) 'a');
        ByteArrayInputStream is = new ByteArrayInputStream(oversized);
        String body = RFC9457Reporter.readBody(is);
        assertEquals(RFC9457Reporter.MAX_BODY_BYTES, body.length());
        assertEquals(4096, is.available());
    }

    @Test
    void readBodyReadsSmallStreamCompletely() throws IOException {
        String payload = "{\"title\":\"gone\"}";
        String body = RFC9457Reporter.readBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        assertEquals(payload, body);
    }

    @Test
    void validProblemDetailsThrowsHttpRFC9457Exception() {
        TestReporter reporter = new TestReporter(true);
        HttpRFC9457Exception exception = assertThrows(
                HttpRFC9457Exception.class,
                () -> reporter.generateException("{\"status\":404,\"title\":\"not found\"}"));
        assertEquals(404, exception.getStatusCode());
        assertEquals("not found", exception.getPayload().getTitle());
    }

    @Test
    void malformedProblemDetailsFallsBackToBaseException() {
        TestReporter reporter = new TestReporter(true);
        HttpTransporterException exception =
                assertThrows(HttpTransporterException.class, () -> reporter.generateException("{ this is not json"));
        assertEquals("HTTP Status: 404 (Not Found)", exception.getMessage());
        assertEquals("{ this is not json", exception.getBodyExtract());
    }

    @Test
    void oversizedProblemDetailsFallsBackToBaseException() {
        char[] padding = new char[RFC9457Reporter.MAX_BODY_BYTES];
        Arrays.fill(padding, 'a');
        // valid problem details if read completely, malformed once truncated at the cap
        String oversized = "{\"title\":\"" + new String(padding) + "\"}";
        TestReporter reporter = new TestReporter(true);
        HttpTransporterException exception =
                assertThrows(HttpTransporterException.class, () -> reporter.generateException(oversized));
        assertEquals("HTTP Status: 404 (Not Found)", exception.getMessage());
    }

    @Test
    void emptyBodyThrowsExceptionWithEmptyPayload() {
        TestReporter reporter = new TestReporter(true);
        HttpRFC9457Exception exception = assertThrows(HttpRFC9457Exception.class, () -> reporter.generateException(""));
        assertSame(RFC9457Payload.INSTANCE, exception.getPayload());
    }

    @Test
    void nonRFC9457Response() {
        TestReporter reporter = new TestReporter(false);
        HttpTransporterException exception = assertThrows(
                HttpTransporterException.class, () -> reporter.generateException("{\"title\":\"ignored\"}"));
        assertFalse(exception instanceof HttpRFC9457Exception);
        assertEquals("HTTP Status: 404 (Not Found)", exception.getMessage());
        assertEquals("{\"title\":\"ignored\"}", exception.getBodyExtract());
    }
}
