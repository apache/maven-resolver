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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;

public class RFC9457Reporter {
    public static final RFC9457Reporter INSTANCE = new RFC9457Reporter();

    /**
     * Maximum number of bytes read from an error response body. Problem details payloads are small, while the body
     * of an error response is arbitrary remote data that the client may have transparently decompressed, so it is
     * never buffered without a bound. Bodies larger than this limit are truncated, not rejected.
     */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    public boolean isRFC9457Message(CloseableHttpResponse response) {
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (headers.length > 0) {
            String contentType = headers[0].getValue();
            return hasRFC9457ContentType(contentType);
        }
        return false;
    }

    /**
     * Throws an {@link HttpRFC9457Exception} carrying the problem details of the given response. Returns normally
     * when the body cannot be parsed as problem details (for example because it was truncated at
     * {@link #MAX_BODY_BYTES}), in which case the caller is expected to report the plain HTTP status instead.
     */
    public void generateException(CloseableHttpResponse response) throws HttpRFC9457Exception {
        int statusCode = getStatusCode(response);
        String reasonPhrase = getReasonPhrase(response);

        String body;
        try {
            body = getBody(response);
        } catch (IOException ignore) {
            // No body found but it is representing a RFC 9457 message due to the content type.
            throw new HttpRFC9457Exception(statusCode, reasonPhrase, RFC9457Payload.INSTANCE);
        }

        if (body != null && !body.isEmpty()) {
            RFC9457Payload rfc9457Payload;
            try {
                rfc9457Payload = RFC9457Parser.parse(body);
            } catch (RuntimeException ignore) {
                // Malformed (possibly truncated, see MAX_BODY_BYTES) problem details must not change the error
                // classification: leave it to the caller to report the plain HTTP status.
                return;
            }
            if (rfc9457Payload != null) {
                throw new HttpRFC9457Exception(statusCode, reasonPhrase, rfc9457Payload);
            }
            return;
        }
        throw new HttpRFC9457Exception(statusCode, reasonPhrase, RFC9457Payload.INSTANCE);
    }

    private String getBody(final CloseableHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return "";
        }
        InputStream is = entity.getContent();
        if (is == null) {
            return "";
        }
        try (InputStream stream = is) {
            return readBody(stream);
        }
    }

    /**
     * Reads the given stream into a UTF-8 string, consuming at most {@link #MAX_BODY_BYTES} bytes. Any remaining
     * bytes are left unread, so an oversized body is truncated instead of being buffered whole. The caller remains
     * responsible for closing the stream.
     *
     * @param is The stream to read the body from, must not be {@code null}.
     * @return The body as UTF-8 string, truncated to {@link #MAX_BODY_BYTES} bytes, never {@code null}.
     * @throws IOException If reading the stream fails.
     */
    static String readBody(InputStream is) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] chunk = new byte[8 * 1024];
        int remaining = MAX_BODY_BYTES;
        while (remaining > 0) {
            int read = is.read(chunk, 0, Math.min(chunk.length, remaining));
            if (read < 0) {
                break;
            }
            body.write(chunk, 0, read);
            remaining -= read;
        }
        return body.toString(StandardCharsets.UTF_8.name());
    }

    private int getStatusCode(final CloseableHttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    private String getReasonPhrase(final CloseableHttpResponse response) {
        String reasonPhrase = response.getStatusLine().getReasonPhrase();
        if (reasonPhrase == null || reasonPhrase.isEmpty()) {
            return "";
        }
        int statusCode = getStatusCode(response);
        return reasonPhrase + " (" + statusCode + ")";
    }

    private boolean hasRFC9457ContentType(String contentType) {
        return "application/problem+json".equals(contentType);
    }
}
