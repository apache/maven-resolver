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
package org.eclipse.aether.transport.apache;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.Rfc9457Reporter;

public class ApacheRfc9457Reporter extends Rfc9457Reporter<CloseableHttpResponse, HttpResponseException> {
    @Override
    protected boolean isRfc9457Message(final CloseableHttpResponse response) {
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (headers.length > 0) {
            String contentType = headers[0].getValue();
            return hasRfc9457ContentType(contentType);
        }
        return false;
    }

    @Override
    protected int getStatusCode(final CloseableHttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    @Override
    protected String getReasonPhrase(final CloseableHttpResponse response) {
        String reasonPhrase = response.getStatusLine().getReasonPhrase();
        if (reasonPhrase == null || reasonPhrase.isEmpty()) {
            return "";
        }
        int statusCode = getStatusCode(response);
        return reasonPhrase + " (" + statusCode + ")";
    }

    @Override
    protected String getBody(final CloseableHttpResponse response) throws IOException {
        return EntityUtils.toString(response.getEntity());
    }
}
