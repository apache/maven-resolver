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

import java.util.Map;
import java.util.Queue;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * Adds system proxy credentials only while selecting authentication for the current route's proxy.
 */
final class SystemProxyAuthenticationStrategy extends ProxyAuthenticationStrategy implements HttpRequestInterceptor {
    private static final String SYSTEM_PROXY = SystemProxyAuthenticationStrategy.class.getName() + ".proxy";

    @Override
    public void process(HttpRequest request, HttpContext context) {
        HttpHost authenticatedProxy = (HttpHost) context.getAttribute(SYSTEM_PROXY);
        RouteInfo route = HttpClientContext.adapt(context).getHttpRoute();
        if (authenticatedProxy != null && route != null && !authenticatedProxy.equals(route.getProxyHost())) {
            // HttpClient retains Basic proxy authentication across redirects, even when the proxy changes.
            AuthState state = HttpClientContext.adapt(context).getProxyAuthState();
            if (state != null) {
                state.reset();
            }
            request.removeHeaders("Proxy-Authorization");
            context.removeAttribute(SYSTEM_PROXY);
        }
    }

    @Override
    public Queue<AuthOption> select(
            Map<String, Header> challenges, HttpHost authhost, HttpResponse response, HttpContext context)
            throws MalformedChallengeException {
        Queue<AuthOption> options = super.select(challenges, authhost, response, context);
        if (!options.isEmpty()) {
            return options;
        }
        RouteInfo route = HttpClientContext.adapt(context).getHttpRoute();
        if (route == null || !authhost.equals(route.getProxyHost())) {
            return options;
        }
        CredentialsProvider credentials = credentials(authhost, "http");
        if (credentials == null) {
            credentials = credentials(authhost, "https");
        }
        if (credentials == null) {
            return options;
        }
        // A child context keeps proxy secrets out of server authentication, including after redirects.
        HttpClientContext proxyContext = HttpClientContext.adapt(new BasicHttpContext(context));
        proxyContext.setCredentialsProvider(credentials);
        options = super.select(challenges, authhost, response, proxyContext);
        if (!options.isEmpty()) {
            context.setAttribute(SYSTEM_PROXY, authhost);
        }
        return options;
    }

    private static CredentialsProvider credentials(HttpHost proxy, String protocol) {
        String prefix = protocol + ".proxy";
        if (!proxy.getHostName().equalsIgnoreCase(System.getProperty(prefix + "Host"))) {
            return null;
        }
        try {
            if (proxy.getPort() != Integer.parseInt(System.getProperty(prefix + "Port"))) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        String username = System.getProperty(prefix + "User");
        if (username == null) {
            return null;
        }
        String password = System.getProperty(prefix + "Password", "");
        BasicCredentialsProvider credentials = new BasicCredentialsProvider();
        credentials.setCredentials(new AuthScope(proxy), new UsernamePasswordCredentials(username, password));
        credentials.setCredentials(
                new AuthScope(proxy, AuthScope.ANY_REALM, AuthSchemes.NTLM),
                new NTCredentials(username, password, null, System.getProperty("http.auth.ntlm.domain")));
        return credentials;
    }
}
