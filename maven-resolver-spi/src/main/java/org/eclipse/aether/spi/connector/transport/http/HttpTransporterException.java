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
package org.eclipse.aether.spi.connector.transport.http;

import org.eclipse.aether.DebugMessageSupplier;

/**
 * Exception thrown by {@link HttpTransporter} in case of non 2xx responses.
 *
 * @since 2.0.0
 */
public class HttpTransporterException extends Exception implements DebugMessageSupplier {
    private final int statusCode;
    private final String reasonPhrase;
    private final String bodyExtract;

    public HttpTransporterException(int statusCode) {
        super(buildMessage(statusCode, null));
        this.statusCode = statusCode;
        this.reasonPhrase = null;
        this.bodyExtract = null;
    }

    public HttpTransporterException(int statusCode, String reasonPhrase) {
        super(buildMessage(statusCode, reasonPhrase));
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.bodyExtract = null;
    }

    public HttpTransporterException(int statusCode, String reasonPhrase, String bodyExtract) {
        super(buildMessage(statusCode, reasonPhrase));
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.bodyExtract = bodyExtract;
    }

    private static String buildMessage(int statusCode, String reasonPhrase) {
        return "HTTP Status: " + statusCode + (reasonPhrase != null ? " (" + reasonPhrase + ")" : "");
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the reason phrase of the response, if available.
     * @return The reason phrase of the response, or {@code null} if not available.
     * @since 2.0.26
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    /**
     * Returns a short extract of the response body, if available.
     * @return A short extract of the response body, or {@code null} if not available.
     * @since 2.0.26
     */
    @Override
    public String getDebugMessage() {
        return bodyExtract != null ? "Response body: " + bodyExtract : "";
    }
}
