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

    private static final class TestReporter extends RFC9457Reporter<String, Object> {
        private final boolean rfc9457;

        private TestReporter(boolean rfc9457) {
            super(true);
            this.rfc9457 = rfc9457;
        }

        @Override
        protected String getContentType(String response) {
            return rfc9457 ? "application/problem+json" : "text/plain";
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
                return readBody(is, getContentType(response));
            }
        }

        @Override
        public void prepareRequest(Object request) {}
    }

    @Test
    void hasRFC9457ContentType() {
        assertTrue(RFC9457Reporter.hasRFC9457ContentType("application/problem+json"));
        assertTrue(RFC9457Reporter.hasRFC9457ContentType("application/problem+json; charset=utf-8"));
    }

    @Test
    void readBodyTruncatesOversizedStream() throws IOException {
        byte[] oversized = new byte[RFC9457Reporter.MAX_BODY_BYTES + 4096];
        Arrays.fill(oversized, (byte) 'a');
        ByteArrayInputStream is = new ByteArrayInputStream(oversized);
        String body = RFC9457Reporter.readBody(is, null);
        assertEquals(RFC9457Reporter.MAX_BODY_BYTES, body.length());
        assertEquals(4096, is.available());
    }

    @Test
    void readBodyReadsSmallStreamCompletely() throws IOException {
        String payload = "{\"title\":\"gone\"}";
        String body =
                RFC9457Reporter.readBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), null);
        assertEquals(payload, body);
    }

    @Test
    void readBodyWithISO_8859_1Charset() throws IOException {
        String payload = "{\"title\":\"&*üÖ\"}";
        String body = RFC9457Reporter.readBody(
                new ByteArrayInputStream(payload.getBytes(StandardCharsets.ISO_8859_1)),
                "application/problem+json; charset=ISO-8859-1");
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
        assertEquals("Response body: { this is not json", exception.getDebugMessage());
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
        assertEquals("Response body: {\"title\":\"ignored\"}", exception.getDebugMessage());
    }

    @Test
    void extractCharsetFromContentType() {
        assertEquals(
                StandardCharsets.UTF_8,
                RFC9457Reporter.extractCharsetFromContentType("text/plain", StandardCharsets.UTF_8));
        assertEquals(
                StandardCharsets.UTF_8,
                RFC9457Reporter.extractCharsetFromContentType("text/plain;Charset=utf-8", StandardCharsets.US_ASCII));
        assertEquals(
                StandardCharsets.ISO_8859_1,
                RFC9457Reporter.extractCharsetFromContentType(
                        "text/plain; charset=ISO-8859-1", StandardCharsets.UTF_8));
        assertEquals(
                StandardCharsets.UTF_8,
                RFC9457Reporter.extractCharsetFromContentType(
                        "text/xml; foo=bar;charset=unknown", StandardCharsets.UTF_8));
        assertEquals(
                StandardCharsets.US_ASCII,
                RFC9457Reporter.extractCharsetFromContentType(
                        "application/xml;charset=ASCII", StandardCharsets.US_ASCII));
    }

    @Test
    void generateBodyExtract() {
        assertEquals(
                "Binary data (application/octet-stream)",
                RFC9457Reporter.generateBodyExtract("ignored", "application/octet-stream"));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", null));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", "text/plain"));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", "Text/Plain"));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", "text/html"));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", "application/atom+xml; charset=utf-8"));
        assertEquals("foo", RFC9457Reporter.generateBodyExtract("foo", "application/problem+json"));
    }
}
