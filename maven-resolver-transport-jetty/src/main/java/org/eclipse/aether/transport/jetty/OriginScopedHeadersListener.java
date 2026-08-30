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
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jetty.client.Request;

/**
 * A client-level request headers listener that scopes operator-configured request headers
 * ({@code aether.transport.http.headers}) and preemptively applied {@code Authorization} to the repository origin.
 * <p>
 * Configured headers are stamped on every request the transporter creates and frequently carry credentials
 * ({@code Authorization}, cookies, private token headers). Jetty's redirector copies the original request headers
 * onto every redirect hop it follows - including hops that leave the repository origin - which would replay those
 * credentials to the redirect target host. Since each Jetty transporter instance is bound to exactly one
 * repository, and copied redirect requests pass through the client-level request listeners again (the same
 * mechanism {@link InsecureRedirectGuard} relies on), removing the scoped headers from any request whose target is
 * not the repository origin (same scheme, case-insensitive host and effective port) confines them to the
 * repository itself. Challenge-based authentication from the authentication store is URI-scoped by Jetty already
 * and is unaffected. Can be disabled via
 * {@link JettyTransporterConfigurationKeys#CONFIG_PROP_ORIGIN_SCOPED_HEADERS}.
 * <p>
 * Registered as a {@link Request.HeadersListener} rather than a {@link Request.QueuedListener} so that the
 * callback fires <em>after</em> Jetty's content decoder factories have populated the {@code Accept-Encoding}
 * header.
 *
 * @since 2.0.22
 */
final class OriginScopedHeadersListener implements Request.HeadersListener {
    private final URI origin;

    private final Set<String> scopedHeaderNames;

    OriginScopedHeadersListener(URI origin, Collection<String> scopedHeaderNames) {
        this.origin = origin;
        this.scopedHeaderNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.scopedHeaderNames.addAll(scopedHeaderNames);
    }

    @Override
    public void onHeaders(Request request) {
        if (scopedHeaderNames.isEmpty()) {
            return;
        }
        URI target = request.getURI();
        // fail closed: when the target cannot be determined, do not send the scoped headers
        if (target == null || !isSameOrigin(origin, target)) {
            request.headers(headers -> {
                for (String scopedHeaderName : scopedHeaderNames) {
                    headers.remove(scopedHeaderName);
                }
            });
        }
    }

    static boolean isSameOrigin(URI origin, URI target) {
        if (origin.getScheme() == null
                || origin.getHost() == null
                || target.getScheme() == null
                || target.getHost() == null) {
            return false;
        }
        return origin.getScheme().equalsIgnoreCase(target.getScheme())
                && origin.getHost().equalsIgnoreCase(target.getHost())
                && effectivePort(origin.getScheme(), origin.getPort())
                        == effectivePort(target.getScheme(), target.getPort());
    }

    /**
     * Determines the effective port: the explicit port if present, otherwise the default port implied by the
     * scheme.
     */
    static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
