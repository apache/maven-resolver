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

import java.io.IOException;

import org.eclipse.jetty.client.Request;

/**
 * A client-level request listener that refuses protocol downgrades for repositories configured with an
 * {@code https} URL. With redirect following enabled (the default), a compromised or malicious repository can
 * answer with a {@code Location: http://...} redirect; Jetty follows it with no scheme constraint, stripping
 * transport encryption from artifact and checksum bytes and making preemptively applied credentials eligible for
 * transmission over plaintext. Since each Jetty transporter instance is bound to exactly one repository, any
 * non-https request issued by its client can only be the result of such a redirect, and is aborted before
 * anything is sent, unless explicitly allowed via
 * {@link JettyTransporterConfigurationKeys#CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS}.
 *
 * @since 2.0.23
 */
final class InsecureRedirectGuard implements Request.QueuedListener {
    @Override
    public void onQueued(Request request) {
        if (!"https".equalsIgnoreCase(request.getScheme())) {
            request.abort(new IOException("Insecure redirect to '" + request.getURI()
                    + "' not followed: protocol downgrade from https to " + request.getScheme()
                    + " would expose credentials and artifact content; set the configuration property "
                    + JettyTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS
                    + "=true to allow it"));
        }
    }
}
