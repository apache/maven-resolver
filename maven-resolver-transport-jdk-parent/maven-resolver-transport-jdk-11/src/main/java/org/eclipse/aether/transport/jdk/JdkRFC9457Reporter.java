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
package org.eclipse.aether.transport.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.RFC9457Reporter;

public class JdkRFC9457Reporter extends RFC9457Reporter<HttpResponse<InputStream>, HttpTransporterException> {
    @Override
    protected boolean isRFC9457Message(final HttpResponse<InputStream> response) {
        Optional<String> optionalContentType = response.headers().firstValue("Content-Type");
        if (optionalContentType.isPresent()) {
            String contentType = optionalContentType.get();
            return hasRFC9457ContentType(contentType);
        }
        return false;
    }

    @Override
    protected int getStatusCode(final HttpResponse<InputStream> response) {
        return response.statusCode();
    }

    @Override
    protected String getReasonPhrase(final HttpResponse<InputStream> response) {
        return null;
    }

    @Override
    protected String getBody(final HttpResponse<InputStream> response) throws IOException {
        try (InputStream is = response.body()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
