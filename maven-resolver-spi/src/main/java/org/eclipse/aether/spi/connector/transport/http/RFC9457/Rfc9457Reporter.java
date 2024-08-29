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
 * A reporter for RFC9457 messages.
 * RFC9457 is a standard for reporting problems in HTTP responses as a JSON object.
 * There are members specified in the RFC but none of those appear to be required,
 * @see <a href=https://www.rfc-editor.org/rfc/rfc9457#section-3-7>rfc9457 section 3.7</a>
 * Given the JSON fields are not mandatory, this reporter simply extracts the body of the
 * response without validation.
 * A RFC9457 message is detected by the content type "application/problem+json".
 *
 * @param <T> The type of the response.
 * @param <E> The base exception type to throw if the response is not a RFC9457 message.
 */
public abstract class Rfc9457Reporter<T, E extends Exception> {
    protected abstract boolean isRfc9457Message(T response);

    protected abstract int getStatusCode(T response);

    protected abstract String getReasonPhrase(T response);

    protected abstract String getBody(T response) throws IOException;

    protected boolean hasRfc9457ContentType(String contentType) {
        return contentType != null && contentType.equals("application/problem+json");
    }
    /**
     * Generates a {@link HttpRfc9457Exception} if the response type is a RFC9457 message.
     * Otherwise, it throws the base exception
     *
     * @param request The response to check for RFC9457 messages.
     * @param baseException The base exception to throw if the response is not a RFC9457 message.
     */
    public void generateException(T request, BiConsumerChecked<Integer, String, E> baseException)
            throws HttpRfc9457Exception, E {
        int statusCode = getStatusCode(request);
        String reasonPhrase = getReasonPhrase(request);
        if (isRfc9457Message(request)) {
            try {
                /* The rfc9457 does not specify a structure to the payload, so we can't
                really do anything with it other than add the content to the exception. */
                String body = getBody(request);
                boolean hasReasonPhrase = reasonPhrase != null && !reasonPhrase.isEmpty();
                boolean hasBody = body != null && !body.isEmpty();

                String message = String.format("status code: %d", statusCode);
                if (hasReasonPhrase) {
                    message += String.format(", reason phrase: %s", reasonPhrase);
                }
                if (hasBody) {
                    message += String.format(", message: %s", body);
                }

                throw new HttpRfc9457Exception(statusCode, reasonPhrase, message);
            } catch (IOException ignored) {
            }
        }
        baseException.accept(statusCode, reasonPhrase);
    }
}
