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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;

import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;

/**
 * A reporter for RFC 9457 messages with falling back to just reporting the status code, reason phrase and a body extract if the response is not a RFC 9457 message.
 * It relies on exception {@link HttpRFC9457Exception} to report RFC 9457 messages and {@link HttpTransporterException} to report non RFC 9457 messages
 * RFC 9457 is a standard for reporting problems in HTTP responses as a JSON object.
 * There are members specified in the RFC but none of those appear to be required,
 * see <a href=https://www.rfc-editor.org/rfc/rfc9457#section-3-7>rfc9457 section 3.7</a>.
 * Given the JSON fields are not mandatory, this reporter simply extracts the body of the
 * response without validation.
 * A RFC 9457 message is detected by the content type {@value #CONTENT_TYPE_PROBLEM_DETAILS_JSON} in the response header.
 *
 * @param <T> The type of the response.
 * @param <R> The type of the request or request builder (which allows to modify headers)
 * @see <a href=https://www.rfc-editor.org/rfc/rfc9457#section-3-7>RFC 9457</a>
 */
public abstract class RFC9457Reporter<T, R> {
    public static final String CONTENT_TYPE_PROBLEM_DETAILS_JSON = "application/problem+json";
    public static final String CONTENT_TYPE_PROBLEM_DETAILS_JSON_AND_ANY = "application/problem+json,*/*";

    /**
     * Maximum number of bytes read from an error response body. Legitimate RFC 9457 payloads are
     * tiny, while the body of an error response is attacker influenced data (the content type header
     * alone triggers consumption) and may be transparently decompressed by the transport, so it must
     * never be buffered unbounded. Bodies larger than this limit are truncated, not rejected.
     */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    private final boolean captureResponseBody;

    /**
     * Creates a new reporter.
     *
     * @param captureResponseBody Whether to capture the response body for non-RFC 9457 messages. If false, the exception will not contain the body extract.
     */
    protected RFC9457Reporter(boolean captureResponseBody) {
        this.captureResponseBody = captureResponseBody;
    }

    /**
     * Returns the content type of the response, or {@code null} if not available.
     * @param response The response to get the content type from.
     * @return The content type of the response, or {@code null} if not available, this should include optional parameters like charset.
     * @since 2.0.26
     */
    protected abstract String getContentType(T response);

    protected abstract int getStatusCode(T response);

    protected abstract String getReasonPhrase(T response);

    /**
     * Returns the body of the response as a string, or {@code null} if not available.
     * The returned string should be truncated to {@link #MAX_BODY_BYTES} bytes, and may be incomplete or malformed.
     * @param response The response to get the body from.
     * @return The body of the response as a string, or {@code null} if not available.
     * @throws IOException If an I/O error occurs while reading the body.
     * @see #readBody(InputStream, String)
     */
    protected abstract String getBody(T response) throws IOException;

    /**
     * Prepares the request to accept RFC 9457 responses.
     * This involves setting/updating the "Accept" header to include "application/problem+json".
     * @param request The request or request builder to prepare
     * @see <a href=https://www.rfc-editor.org/rfc/rfc9457#section-3-2>RFC 9457 section 3.2</a>
     */
    public abstract void prepareRequest(R request);

    /**
     * Reads the given stream into a string, consuming at most {@link #MAX_BODY_BYTES} bytes.
     * Any remaining bytes are left unread, so an oversized (or decompression amplified) body is
     * truncated instead of exhausting memory. The caller remains responsible for closing the stream.
     *
     * @param is The stream to read the body from, must not be {@code null}.
     * @param contentType The content type of the body, may be {@code null} if unknown.
     * @return The body as UTF-8 string, truncated to {@link #MAX_BODY_BYTES} bytes, never {@code null}.
     * @throws IOException If reading the stream fails.
     */
    protected static String readBody(InputStream is, String contentType) throws IOException {
        // The RFC 9457 content type is always UTF-8, but the server may lie and send a different charset.
        // We ignore the charset and always read as UTF-8, because the RFC 9457 payload is always UTF-8.
        // If the server sends a different charset, we may get garbage, but we don't want to risk
        // misinterpreting the payload and failing to report a valid RFC 9457 message.
        final Charset charset = extractCharsetFromContentType(contentType, StandardCharsets.UTF_8);
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
        return new String(body.toByteArray(), charset);
    }

    protected static Charset extractCharsetFromContentType(String contentType, Charset defaultCharset) {
        if (contentType == null) {
            return defaultCharset;
        }

        int paramsStart = contentType.indexOf(';');
        if (paramsStart < 0 || paramsStart == contentType.length() - 1) {
            return defaultCharset;
        }

        String[] parameters = contentType.substring(paramsStart + 1).split(";");
        for (String parameter : parameters) {
            String trimmedParameter = parameter.trim();
            if (trimmedParameter.isEmpty()) {
                continue;
            }

            int separator = trimmedParameter.indexOf('=');
            if (separator <= 0 || separator == trimmedParameter.length() - 1) {
                continue;
            }

            String name = trimmedParameter.substring(0, separator).trim();
            if (!"charset".equalsIgnoreCase(name)) {
                continue;
            }

            String value = trimmedParameter.substring(separator + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).trim();
            }

            if (value.isEmpty()) {
                continue;
            }

            try {
                return Charset.forName(value);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                // ignore invalid charset and use default charset
                return defaultCharset;
            }
        }

        return defaultCharset;
    }

    static boolean hasRFC9457ContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        // strip off parameters
        int idx = contentType.indexOf(';');
        if (idx > -1) {
            contentType = contentType.substring(0, idx);
        }
        return CONTENT_TYPE_PROBLEM_DETAILS_JSON.equals(contentType);
    }

    /**
     * Generates a {@link HttpRFC9457Exception} if the response type is a RFC 9457 message.
     * Otherwise, it throws an {@link HttpTransporterException} with the status code and reason phrase.
     *
     * @param response The response to check for RFC 9457 messages.
     */
    public void generateException(T response) throws HttpTransporterException {
        int statusCode = getStatusCode(response);
        String reasonPhrase = getReasonPhrase(response);

        final String body;
        final String contentType = getContentType(response);
        if (hasRFC9457ContentType(contentType)) {
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
                    // Malformed (possibly truncated, see MAX_BODY_BYTES) problem details
                    // must not change the error classification.
                    rfc9457Payload = null;
                }
                if (rfc9457Payload != null) {
                    throw new HttpRFC9457Exception(statusCode, reasonPhrase, rfc9457Payload);
                } else {
                    throw new HttpTransporterException(
                            statusCode, reasonPhrase, generateBodyExtract(body, contentType));
                }
            } else {
                // No body found but it is representing a RFC 9457 message due to the content type.
                throw new HttpRFC9457Exception(statusCode, reasonPhrase, RFC9457Payload.INSTANCE);
            }
        } else {
            try {
                if (!captureResponseBody) {
                    throw new HttpTransporterException(statusCode, reasonPhrase);
                } else {
                    body = getBody(response);
                    throw new HttpTransporterException(
                            statusCode, reasonPhrase, generateBodyExtract(body, contentType));
                }
            } catch (IOException e) {
                throw new HttpTransporterException(statusCode, reasonPhrase);
            }
        }
    }

    /**
     * Generates a body extract from the given body string (usually HTML).
     * For text-based content types ({@code text/*}, {@code *\/json}, {@code *\/xml}) the body string is returned as-is.
     * For binary content types a placeholder message is returned to avoid embedding non-printable data in the exception.
     *
     * @param body The body string to generate the extract from (already supposed to be truncated to {@link #MAX_BODY_BYTES} bytes).
     * @param contentType The content type of the body, may be {@code null} if unknown.
     * @return The generated body extract.
     */
    public static String generateBodyExtract(String body, String contentType) {
        if (contentType == null) {
            return body;
        }
        // strip off parameters
        if (contentType.indexOf(';') > 0) {
            contentType = contentType.substring(0, contentType.indexOf(';')).trim();
        }
        final String lowerCaseContentType = contentType.toLowerCase(Locale.ROOT);
        if (lowerCaseContentType.equals("text")
                || lowerCaseContentType.startsWith("text/")
                || lowerCaseContentType.endsWith("/json")
                || lowerCaseContentType.endsWith("+json")
                || lowerCaseContentType.endsWith("/xml")
                || lowerCaseContentType.endsWith("+xml")) {
            return body;
        } else {
            return "Binary data (" + contentType + ")";
        }
    }
}
