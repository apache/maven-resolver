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

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Credentials provider that helps to isolate server from proxy credentials. Apache HttpClient uses a single provider
 * for both server and proxy auth, using the auth scope (host, port, etc.) to select the proper credentials. With regard
 * to redirects, we use an auth scope for server credentials that's not specific enough to not be mistaken for proxy
 * auth. This provider helps to maintain the proper isolation.
 */
final class DemuxCredentialsProvider implements CredentialsStore {

    private final CredentialsStore serverCredentialsProvider;

    private final CredentialsStore proxyCredentialsProvider;

    private final HttpHost proxy;

    DemuxCredentialsProvider(
            CredentialsStore serverCredentialsProvider, CredentialsStore proxyCredentialsProvider, HttpHost proxy) {
        this.serverCredentialsProvider = serverCredentialsProvider;
        this.proxyCredentialsProvider = proxyCredentialsProvider;
        this.proxy = proxy;
    }

    private CredentialsStore getDelegate(AuthScope authScope) {
        if (proxy.getPort() == authScope.getPort() && proxy.getHostName().equalsIgnoreCase(authScope.getHost())) {
            return proxyCredentialsProvider;
        }
        return serverCredentialsProvider;
    }

    @Override
    public Credentials getCredentials(AuthScope authScope, HttpContext context) {
        return getDelegate(authScope).getCredentials(authScope, context);
    }

    @Override
    public void setCredentials(AuthScope authScope, Credentials credentials) {
        getDelegate(authScope).setCredentials(authScope, credentials);
    }

    @Override
    public void clear() {
        serverCredentialsProvider.clear();
        proxyCredentialsProvider.clear();
    }
}
