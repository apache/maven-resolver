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
package org.eclipse.aether.transport.apache5x;

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.core5.http.HttpHost;

/**
 * Auth scheme cache that upon clearing releases all cached schemes into a pool for future reuse by other requests,
 * thereby reducing challenge-response roundtrips.
 */
final class SharingAuthCache implements AuthCache {

    private final LocalState state;

    private final Map<HttpHost, AuthScheme> authSchemes;

    SharingAuthCache(LocalState state) {
        this.state = state;
        authSchemes = new HashMap<>();
    }

    private static HttpHost toKey(HttpHost host) {
        if (host.getPort() <= 0) {
            int port = host.getSchemeName().equalsIgnoreCase("https") ? 443 : 80;
            return new HttpHost(host.getSchemeName(), host.getHostName(), port);
        }
        return host;
    }

    @Override
    public AuthScheme get(HttpHost host) {
        host = toKey(host);
        AuthScheme authScheme = authSchemes.get(host);
        if (authScheme == null) {
            authScheme = state.getAuthScheme(host);
            authSchemes.put(host, authScheme);
        }
        return authScheme;
    }

    @Override
    public void put(HttpHost host, AuthScheme authScheme) {
        if (authScheme != null) {
            authSchemes.put(toKey(host), authScheme);
        } else {
            remove(host);
        }
    }

    @Override
    public void remove(HttpHost host) {
        authSchemes.remove(toKey(host));
    }

    @Override
    public void clear() {
        share();
        authSchemes.clear();
    }

    private void share() {
        for (Map.Entry<HttpHost, AuthScheme> entry : authSchemes.entrySet()) {
            state.setAuthScheme(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String toString() {
        return authSchemes.toString();
    }
}
