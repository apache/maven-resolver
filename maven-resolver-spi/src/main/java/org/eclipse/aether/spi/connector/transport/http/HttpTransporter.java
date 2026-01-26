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

import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.Transporter;

/**
 * A transporter using HTTP protocol.
 *
 * @since 2.0.0
 */
public interface HttpTransporter extends Transporter {

    /**
     * Transport property keys specific to HTTP transporters.
     * @see org.eclipse.aether.spi.connector.transport.TransporterListener#transportStarted(long, long, java.util.Map)
     */
    enum HttpTransportPropertyKey implements TransportListener.TransportPropertyKey {
        /**
         * Transport property key for HTTP version. Value is a String representing the HTTP version used (e.g., "HTTP/1.1", "HTTP/2").
         */
        HTTP_VERSION,
        /**
         * Transport property key for SSL protocol. Value is a String representing the SSL protocol used (e.g., "TLSv1.2", "TLSv1.3").
         */
        SSL_PROTOCOL,
        /**
         * Transport property key for SSL cipher suite. Value is a String representing the SSL cipher suite used (e.g., "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256").
         */
        SSL_CIPHER_SUITE,
        /**
         * Transport property key for content coding (usually compression). Value is a String representing the compression algorithm used (e.g., "gzip", "br", or "zstd")
         * @see <a href="https://www.iana.org/assignments/http-parameters/http-parameters.xhtml#content-coding">Content Coding Values</a>
         */
        CONTENT_CODING,
        /**
         * Transport property key for number of bytes transferred. Value is a Long representing the total number of bytes transferred during the transport operation.
         * This may be less than the content length in case of compression.
         */
        NUM_BYTES_TRANSFERRED;
    }
}
