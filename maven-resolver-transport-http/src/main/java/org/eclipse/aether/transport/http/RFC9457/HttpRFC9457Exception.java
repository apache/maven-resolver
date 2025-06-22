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

/**
 * Exception thrown by {@link HttpTransporter} in case of errors.
 *
 * @since 1.9.24
 */
public class HttpRFC9457Exception extends IOException {
    private final int statusCode;

    private final String reasonPhrase;

    private final RFC9457Payload payload;

    public HttpRFC9457Exception(int statusCode, String reasonPhrase, RFC9457Payload payload) {
        super(payload.toString());
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.payload = payload;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public RFC9457Payload getPayload() {
        return payload;
    }
}
