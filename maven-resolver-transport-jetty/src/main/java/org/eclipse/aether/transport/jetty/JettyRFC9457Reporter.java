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
package org.eclipse.aether.transport.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.RFC9457Reporter;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpHeader;

public class JettyRFC9457Reporter extends RFC9457Reporter<InputStreamResponseListener, HttpTransporterException> {
    public static final JettyRFC9457Reporter INSTANCE = new JettyRFC9457Reporter();

    @Override
    protected boolean isRFC9457Message(final InputStreamResponseListener listener) {
        try {
            Response response = listener.get(1, TimeUnit.SECONDS);
            String contentType = response.getHeaders().get(HttpHeader.CONTENT_TYPE);
            return hasRFC9457ContentType(contentType);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            return false;
        }
    }

    @Override
    protected int getStatusCode(final InputStreamResponseListener listener) {
        try {
            Response response = listener.get(1, TimeUnit.SECONDS);
            return response.getStatus();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            return -1;
        }
    }

    @Override
    protected String getReasonPhrase(final InputStreamResponseListener listener) {
        try {
            Response response = listener.get(1, TimeUnit.SECONDS);
            return response.getReason();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            return null;
        }
    }

    @Override
    protected String getBody(final InputStreamResponseListener listener) throws IOException {
        try (InputStream is = listener.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
