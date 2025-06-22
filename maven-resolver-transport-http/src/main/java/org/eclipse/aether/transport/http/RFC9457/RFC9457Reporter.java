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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

public class RFC9457Reporter {
    public static final RFC9457Reporter INSTANCE = new RFC9457Reporter();

    public boolean isRFC9457Message(CloseableHttpResponse response) {
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (headers.length > 0) {
            String contentType = headers[0].getValue();
            return hasRFC9457ContentType(contentType);
        }
        return false;
    }

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
            RFC9457Payload rfc9457Payload = RFC9457Parser.parse(body);
            throw new HttpRFC9457Exception(statusCode, reasonPhrase, rfc9457Payload);
        }
        throw new HttpRFC9457Exception(statusCode, reasonPhrase, RFC9457Payload.INSTANCE);
    }

    private String getBody(final CloseableHttpResponse response) throws IOException {
        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
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
