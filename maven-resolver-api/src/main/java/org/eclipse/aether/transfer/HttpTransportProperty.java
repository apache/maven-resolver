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
package org.eclipse.aether.transfer;

/**
 * Defines transport property keys specific to HTTP transporters.
 * These keys can be used to expose additional information about the HTTP transport operation.
 * Additionally it defines types for the values of these keys, such as {@link HttpVersion} and {@link SslProtocol}.
 * @see TransferEvent#getTransportProperties()
 * @since 2.0.21
 */
public final class HttpTransportProperty {

    private HttpTransportProperty() {
        // Private constructor to prevent instantiation
    }

    /**
     * Transport property keys specific to HTTP transporters.
     * @see TransferEvent#getTransportProperties()
     */
    public enum Key implements TransferEvent.TransportPropertyKey {
        /**
         * Transport property key for HTTP version. Value is a {@link HttpVersion} representing the HTTP version used.
         */
        HTTP_VERSION,
        /**
         * Transport property key for SSL protocol. Value is a {@link SslProtocol} representing the SSL protocol used.
         */
        SSL_PROTOCOL,
        /**
         * Transport property key for SSL cipher suite. Value is a String representing the SSL cipher suite used (e.g., "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256").
         * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#jsse-cipher-suite-names">JSSE Cipher Suite Names</a>.
         */
        SSL_CIPHER_SUITE,
        /**
         * Transport property key for content coding (usually compression). Value is a String representing the compression algorithm used (e.g., "gzip", "br", or "zstd")
         * @see <a href="https://www.iana.org/assignments/http-parameters/http-parameters.xhtml#content-coding">Content Coding Values</a>
         */
        CONTENT_CODING,
    }

    /**
     * HTTP version used for the HTTP transport.
     */
    public enum HttpVersion {
        HTTP_1_0("HTTP/1.0"),
        HTTP_1_1("HTTP/1.1"),
        HTTP_2("HTTP/2"),
        HTTP_3("HTTP/3");

        private final String label;

        HttpVersion(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * SSL protocol including version used for the HTTP transport.
     */
    public enum SslProtocol {
        SSL_3_0("SSLv3"),
        TLS_1_0("TLSv1"),
        TLS_1_1("TLSv1.1"),
        TLS_1_2("TLSv1.2"),
        TLS_1_3("TLSv1.3");

        private final String label;

        SslProtocol(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        /**
         * Converts a standard SSL protocol name to the corresponding SslProtocol enum value.
         *
         * @param name the standard algorithm name of the SSL protocol (e.g., "TLSv1.2", "TLSv1.3")
         * @return the corresponding SslProtocol enum value
         * @see <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#sslcontext-algorithms">Standard Names for SSLContext Algorithms</a>
         */
        public static SslProtocol fromStandardName(String name) {
            switch (name) {
                case "SSLv3":
                    return SSL_3_0;
                case "TLSv1":
                    return TLS_1_0;
                case "TLSv1.1":
                    return TLS_1_1;
                case "TLSv1.2":
                    return TLS_1_2;
                case "TLSv1.3":
                    return TLS_1_3;
                default:
                    throw new IllegalArgumentException("Unknown SSL protocol: " + name);
            }
        }
    }
}
