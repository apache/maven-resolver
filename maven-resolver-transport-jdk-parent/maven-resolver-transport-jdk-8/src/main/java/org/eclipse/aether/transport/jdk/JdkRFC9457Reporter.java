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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.RFC9457Reporter;

public class JdkRFC9457Reporter extends RFC9457Reporter<HttpURLConnection, HttpTransporterException> {
    @Override
    protected boolean isRFC9457Message(final HttpURLConnection response) {
        String contentType = response.getContentType();
        return hasRFC9457ContentType(contentType);
    }

    @Override
    protected int getStatusCode(final HttpURLConnection response) {
        try {
            return response.getResponseCode();
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    protected String getReasonPhrase(final HttpURLConnection response) {
        return null;
    }

    @Override
    protected String getBody(final HttpURLConnection response) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
