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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.transfer.HttpTransportProperty.HttpVersion;
import org.eclipse.aether.transfer.HttpTransportProperty.Key;
import org.eclipse.aether.transfer.HttpTransportProperty.SslProtocol;
import org.eclipse.aether.transfer.TransferEvent;

/**
 * Builder for HTTP transport properties used in {@link TransportListener#transportStarted(long, long, Map)}.
 * @since NEXT
 */
public final class HttpTransportPropertiesBuilder {
    Map<TransferEvent.TransportPropertyKey, Object> properties = new HashMap<>();

    public HttpTransportPropertiesBuilder(HttpVersion version) {
        this.properties.put(Key.HTTP_VERSION, version);
    }

    public HttpTransportPropertiesBuilder withSslProtocol(String name) {
        return withSslProtocol(SslProtocol.fromStandardName(name));
    }

    public HttpTransportPropertiesBuilder withSslProtocol(SslProtocol sslProtocol) {
        this.properties.put(Key.SSL_PROTOCOL, sslProtocol);
        return this;
    }

    public HttpTransportPropertiesBuilder withSslCipherSuite(String cipherSuite) {
        this.properties.put(Key.SSL_CIPHER_SUITE, cipherSuite);
        return this;
    }

    public HttpTransportPropertiesBuilder withContentCoding(String contentCoding) {
        this.properties.put(Key.CONTENT_CODING, contentCoding);
        return this;
    }

    public HttpTransportPropertiesBuilder withNumBytesTransferred(long numBytes) {
        this.properties.put(Key.NUM_BYTES_TRANSFERRED, numBytes);
        return this;
    }

    public Map<TransferEvent.TransportPropertyKey, Object> build() {
        return Collections.unmodifiableMap(properties);
    }
}
