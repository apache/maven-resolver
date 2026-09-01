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
package org.eclipse.aether.transport.apache5;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

/**
 * Scopes operator-configured request headers ({@code aether.transport.http.headers}) to the repository origin.
 * <p>
 * Configured headers are stamped on every request the transport creates and frequently carry credentials
 * ({@code Authorization}, cookies, private token headers). Apache HttpClient's redirect execution re-sends the
 * original request headers on each redirect hop, including hops that leave the repository origin, which would
 * replay those credentials to the redirect target. This interceptor runs in the protocol layer - which HttpClient
 * re-enters for every redirect hop - and removes the configured headers from any request whose target host is not
 * the repository origin (same scheme, host and effective port). Challenge- and preemptive-authentication headers
 * are attached below the protocol layer and are host-scoped by the credentials provider already; they are not
 * affected by this interceptor.
 *
 * @since 2.0.23
 */
final class OriginScopedHeadersInterceptor implements HttpRequestInterceptor {
    private final HttpHost origin;

    private final Set<String> headerNames;

    OriginScopedHeadersInterceptor(HttpHost origin, Collection<?> headerNames) {
        this.origin = origin;
        this.headerNames = new HashSet<>();
        for (Object headerName : headerNames) {
            if (headerName != null) {
                this.headerNames.add(String.valueOf(headerName));
            }
        }
    }

    @Override
    public void process(HttpRequest request, HttpContext context) {
        if (headerNames.isEmpty()) {
            return;
        }
        Object attribute = context != null ? context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST) : null;
        // fail closed: when the target host cannot be determined, do not attach configured headers
        if (!(attribute instanceof HttpHost) || !isSameOrigin(origin, (HttpHost) attribute)) {
            for (String headerName : headerNames) {
                request.removeHeaders(headerName);
            }
        }
    }

    static boolean isSameOrigin(HttpHost origin, HttpHost target) {
        return origin.getSchemeName().equalsIgnoreCase(target.getSchemeName())
                && origin.getHostName().equalsIgnoreCase(target.getHostName())
                && schemeDefaultPort(origin) == schemeDefaultPort(target);
    }

    /**
     * Determines the effective port of the given host: the explicit port if present, otherwise the default port
     * implied by the scheme.
     */
    static int schemeDefaultPort(HttpHost host) {
        if (host.getPort() >= 0) {
            return host.getPort();
        }
        return "https".equalsIgnoreCase(host.getSchemeName()) ? 443 : 80;
    }
}
