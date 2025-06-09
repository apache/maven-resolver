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
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.RFC9457Reporter;

public class ApacheRFC9457Reporter extends RFC9457Reporter<ClassicHttpResponse, HttpResponseException> {
    public static final ApacheRFC9457Reporter INSTANCE = new ApacheRFC9457Reporter();

    private ApacheRFC9457Reporter() {}

    @Override
    protected boolean isRFC9457Message(final ClassicHttpResponse response) {
        Header[] headers = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (headers.length > 0) {
            String contentType = headers[0].getValue();
            return hasRFC9457ContentType(contentType);
        }
        return false;
    }

    @Override
    protected int getStatusCode(final ClassicHttpResponse response) {
        return response.getCode();
    }

    @Override
    protected String getReasonPhrase(final ClassicHttpResponse response) {
        String reasonPhrase = response.getReasonPhrase();
        if (reasonPhrase == null || reasonPhrase.isEmpty()) {
            return "";
        }
        int statusCode = getStatusCode(response);
        return reasonPhrase + " (" + statusCode + ")";
    }

    @Override
    protected String getBody(final ClassicHttpResponse response) throws IOException {
        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new IOException(e);
        }
    }
}
