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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.eclipse.aether.repository.AuthenticationContext;

/**
 * Credentials provider that defers calls into the auth context until authentication is actually requested.
 */
final class DeferredCredentialsProvider implements CredentialsStore {

    private final CredentialsStore delegate;

    private final Map<AuthScope, Factory> factories;

    DeferredCredentialsProvider() {
        delegate = new BasicCredentialsProvider();
        factories = new HashMap<>();
    }

    public void setCredentials(AuthScope authScope, Factory factory) {
        factories.put(authScope, factory);
    }

    @Override
    public void setCredentials(AuthScope authScope, Credentials credentials) {
        delegate.setCredentials(authScope, credentials);
    }

    @Override
    public Credentials getCredentials(AuthScope authScope, HttpContext context) {
        synchronized (factories) {
            for (Iterator<Map.Entry<AuthScope, Factory>> it =
                            factories.entrySet().iterator();
                    it.hasNext(); ) {
                Map.Entry<AuthScope, Factory> entry = it.next();
                if (authScope.match(entry.getKey()) >= 0) {
                    it.remove();
                    delegate.setCredentials(entry.getKey(), entry.getValue().newCredentials());
                }
            }
        }
        return delegate.getCredentials(authScope, context);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    interface Factory {

        Credentials newCredentials();
    }

    static class BasicFactory implements Factory {

        private final AuthenticationContext authContext;

        BasicFactory(AuthenticationContext authContext) {
            this.authContext = authContext;
        }

        @Override
        public Credentials newCredentials() {
            String username = authContext.get(AuthenticationContext.USERNAME);
            if (username == null) {
                return null;
            }
            String password = authContext.get(AuthenticationContext.PASSWORD);
            return new UsernamePasswordCredentials(username, password.toCharArray());
        }
    }
}
