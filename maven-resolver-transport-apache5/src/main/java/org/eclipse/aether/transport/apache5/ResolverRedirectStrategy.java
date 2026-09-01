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

import java.net.URI;

import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A redirect strategy that refuses protocol downgrades: a redirect from an {@code https} URL to an {@code http}
 * URL strips transport encryption from artifact and checksum bytes and makes repository credentials eligible for
 * transmission over plaintext, so it fails the transfer instead of being followed silently, unless explicitly
 * allowed via {@link ApacheTransporterConfigurationKeys#CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS}. All other
 * redirects behave exactly as {@link DefaultRedirectStrategy} handles them.
 *
 * @since 2.0.23
 */
final class ResolverRedirectStrategy extends DefaultRedirectStrategy {
    private final boolean followInsecureRedirects;

    ResolverRedirectStrategy(boolean followInsecureRedirects) {
        this.followInsecureRedirects = followInsecureRedirects;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        boolean redirected = super.isRedirected(request, response, context);
        if (redirected && !followInsecureRedirects) {
            URI location;
            try {
                location = getLocationURI(request, response, context);
            } catch (HttpException e) {
                throw new ProtocolException("Invalid redirect location: " + e.getMessage(), e);
            }
            if (location != null) {
                String locationScheme = location.getScheme();
                String currentScheme = currentScheme(request, context);
                if (locationScheme != null
                        && "https".equalsIgnoreCase(currentScheme)
                        && !"https".equalsIgnoreCase(locationScheme)) {
                    throw new ProtocolException("Insecure redirect to '" + location
                            + "' not followed: protocol downgrade from https to " + locationScheme
                            + " would expose credentials and artifact content; set the configuration property "
                            + ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS
                            + "=true to allow it");
                }
            }
        }
        return redirected;
    }

    private static String currentScheme(HttpRequest request, HttpContext context) {
        if (context != null) {
            HttpClientContext clientContext = HttpClientContext.cast(context);
            if (clientContext.getHttpRoute() != null) {
                return clientContext.getHttpRoute().getTargetHost().getSchemeName();
            }
        }
        if (request.getScheme() != null) {
            return request.getScheme();
        }
        try {
            URI uri = request.getUri();
            if (uri != null && uri.isAbsolute()) {
                return uri.getScheme();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
