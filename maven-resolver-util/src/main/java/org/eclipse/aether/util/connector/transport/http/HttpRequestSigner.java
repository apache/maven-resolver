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
package org.eclipse.aether.util.connector.transport.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Signs HTTP requests of a repository by adding headers computed from the request about to be sent. Transports
 * call {@link #sign(String, URI)} after all other headers are set and apply the returned headers, and they never
 * carry the headers named by {@link #signedHeaderNames()} to a request that goes to another origin.
 *
 * @see HttpTransporterUtils#getHttpRequestSigner(org.eclipse.aether.RepositorySystemSession,
 *      org.eclipse.aether.repository.RemoteRepository)
 * @since 2.0.26
 */
public interface HttpRequestSigner {
    /**
     * Computes the headers that sign the given request.
     *
     * @param method the HTTP method, never {@code null}
     * @param uri the absolute request URI as the transport will send it, never {@code null}
     * @return the headers to set on the request, replacing any header of the same name; never {@code null}
     */
    Map<String, String> sign(String method, URI uri);

    /**
     * The names of the headers {@link #sign(String, URI)} may return, compared case-insensitively. Transports remove
     * them before signing a request again and before sending a request to another origin.
     */
    Set<String> signedHeaderNames();
}
