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

import java.net.URI;

import org.eclipse.aether.util.connector.transport.TransporterUtils;
import org.eclipse.aether.util.connector.transport.http.HttpRequestSigner;
import org.eclipse.jetty.client.Request;

/**
 * Signs every request of the client, including the redirect hops Jetty follows itself, right before its headers
 * are sent: the previous signature is removed, and a new one is computed only for requests that stay within the
 * repository origin, so a cross-origin hop carries no signature.
 */
final class SigningHeadersListener implements Request.HeadersListener {
    private final URI origin;

    private final HttpRequestSigner requestSigner;

    SigningHeadersListener(URI origin, HttpRequestSigner requestSigner) {
        this.origin = origin;
        this.requestSigner = requestSigner;
    }

    @Override
    public void onHeaders(Request request) {
        request.headers(headers -> {
            for (String name : requestSigner.signedHeaderNames()) {
                headers.remove(name);
            }
        });
        URI target = request.getURI();
        if (target != null && TransporterUtils.isSameOrigin(origin, target)) {
            requestSigner
                    .sign(request.getMethod(), target)
                    .forEach((name, value) -> request.headers(headers -> headers.put(name, value)));
        }
    }
}
