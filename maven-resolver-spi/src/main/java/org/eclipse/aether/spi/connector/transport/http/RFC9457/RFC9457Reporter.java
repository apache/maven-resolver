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

import java.io.IOException;

/**
 * A reporter for RFC 9457 messages.
 * RFC9457 is a standard for reporting problems in HTTP responses as a JSON object.
 * There are members specified in the RFC but none of those appear to be required,
 * @see <a href=https://www.rfc-editor.org/rfc/rfc9457#section-3-7>rfc9457 section 3.7</a>
 * Given the JSON fields are not mandatory, this reporter simply extracts the body of the
 * response without validation.
 * A RFC 9457 message is detected by the content type "application/problem+json".
 *
 * @param <T> The type of the response.
 * @param <E> The base exception type to throw if the response is not a RFC9457 message.
 */
public abstract class RFC9457Reporter<T, E extends Exception> {
    protected abstract boolean isRFC9457Message(T response);

    protected abstract int getStatusCode(T response);

    protected abstract String getReasonPhrase(T response);

    protected abstract String getBody(T response) throws IOException;

    protected boolean hasRFC9457ContentType(String contentType) {
        return "application/problem+json".equals(contentType);
    }

    /**
     * Generates a {@link HttpRFC9457Exception} if the response type is a RFC 9457 message.
     * Otherwise, it throws the base exception
     *
     * @param response The response to check for RFC 9457 messages.
     * @param baseException The base exception to throw if the response is not a RFC 9457 message.
     */
    public void generateException(T response, BiConsumerChecked<Integer, String, E> baseException)
            throws E, HttpRFC9457Exception {
        int statusCode = getStatusCode(response);
        String reasonPhrase = getReasonPhrase(response);

        if (isRFC9457Message(response)) {
            String body;
            try {
                body = getBody(response);
            } catch (IOException ignore) {
                // No body found but it is representing a RFC 9457 message due to the content type.
                throw new HttpRFC9457Exception(statusCode, reasonPhrase, new RFC9457Payload());
            }

            if (body != null && !body.isEmpty()) {
                RFC9457Payload rfc9457Payload = RFC9457Parser.parse(body);
                throw new HttpRFC9457Exception(statusCode, reasonPhrase, rfc9457Payload);
            }
            throw new HttpRFC9457Exception(statusCode, reasonPhrase, new RFC9457Payload());
        }
        baseException.accept(statusCode, reasonPhrase);
    }
}
