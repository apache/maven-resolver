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
package org.eclipse.aether.transport.apache;

import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * A redirect strategy that refuses protocol downgrades: a redirect from an {@code https} URL to an {@code http}
 * URL strips transport encryption from artifact and checksum bytes and makes repository credentials eligible for
 * transmission over plaintext, so it fails the transfer instead of being followed silently, unless explicitly
 * allowed via {@link ApacheTransporterConfigurationKeys#CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS}. All other
 * redirects behave exactly as {@link LaxRedirectStrategy} handled them before.
 *
 * @since 2.0.23
 */
final class ResolverRedirectStrategy extends LaxRedirectStrategy {
    private final boolean followInsecureRedirects;

    ResolverRedirectStrategy(boolean followInsecureRedirects) {
        this.followInsecureRedirects = followInsecureRedirects;
    }

    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        boolean redirected = super.isRedirected(request, response, context);
        if (redirected && !followInsecureRedirects) {
            Header locationHeader = response.getFirstHeader("location");
            if (locationHeader != null) {
                URI location = createLocationURI(locationHeader.getValue());
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
        HttpHost target = context != null ? HttpClientContext.adapt(context).getTargetHost() : null;
        if (target != null) {
            return target.getSchemeName();
        }
        if (request instanceof HttpUriRequest) {
            URI uri = ((HttpUriRequest) request).getURI();
            if (uri != null && uri.isAbsolute()) {
                return uri.getScheme();
            }
        }
        return null;
    }
}
