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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.DefaultHttpClientConnectionOperator;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLInitializationException;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Container for HTTP-related state that can be shared across incarnations of the transporter to optimize the
 * communication with servers.
 */
final class GlobalState implements Closeable {

    static class CompoundKey {

        private final Object[] keys;

        CompoundKey(Object... keys) {
            this.keys = keys;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || !getClass().equals(obj.getClass())) {
                return false;
            }
            CompoundKey that = (CompoundKey) obj;
            return Arrays.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = hash * 31 + Arrays.hashCode(keys);
            return hash;
        }

        @Override
        public String toString() {
            return Arrays.toString(keys);
        }
    }

    private static final String KEY = GlobalState.class.getName();

    private static final String CONFIG_PROP_CACHE_STATE =
            ApacheTransporterConfigurationKeys.CONFIG_PROPS_PREFIX + "cacheState";

    private final ConcurrentMap<ConnMgrConfig, HttpClientConnectionManager> connectionManagers;

    private final ConcurrentMap<CompoundKey, Object> userTokens;

    private final ConcurrentMap<CompoundKey, Boolean> expectContinues;

    public static GlobalState get(RepositorySystemSession session) {
        GlobalState cache;
        RepositoryCache repoCache = session.getCache();
        if (repoCache == null || !ConfigUtils.getBoolean(session, true, CONFIG_PROP_CACHE_STATE)) {
            cache = null;
        } else {
            Object tmp = repoCache.get(session, KEY);
            if (tmp instanceof GlobalState) {
                cache = (GlobalState) tmp;
            } else {
                synchronized (GlobalState.class) {
                    tmp = repoCache.get(session, KEY);
                    if (tmp instanceof GlobalState) {
                        cache = (GlobalState) tmp;
                    } else {
                        cache = new GlobalState();
                        repoCache.put(session, KEY, cache);
                    }
                }
            }
        }
        return cache;
    }

    private GlobalState() {
        connectionManagers = new ConcurrentHashMap<>();
        userTokens = new ConcurrentHashMap<>();
        expectContinues = new ConcurrentHashMap<>();
    }

    @Override
    public void close() {
        for (Iterator<Map.Entry<ConnMgrConfig, HttpClientConnectionManager>> it =
                        connectionManagers.entrySet().iterator();
                it.hasNext(); ) {
            HttpClientConnectionManager connMgr = it.next().getValue();
            it.remove();
            connMgr.shutdown();
        }
    }

    public HttpClientConnectionManager getConnectionManager(ConnMgrConfig config) {
        return connectionManagers.computeIfAbsent(config, GlobalState::newConnectionManager);
    }

    public static HttpClientConnectionManager newConnectionManager(ConnMgrConfig connMgrConfig) {
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory());
        int connectionMaxTtlSeconds = ConfigurationProperties.DEFAULT_HTTP_CONNECTION_MAX_TTL;
        int maxConnectionsPerRoute = ConfigurationProperties.DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE;

        if (connMgrConfig == null) {
            registryBuilder.register("https", SSLConnectionSocketFactory.getSystemSocketFactory());
        } else {
            // config present: use provided, if any, or create (depending on httpsSecurityMode)
            connectionMaxTtlSeconds = connMgrConfig.connectionMaxTtlSeconds;
            maxConnectionsPerRoute = connMgrConfig.maxConnectionsPerRoute;
            SSLSocketFactory sslSocketFactory =
                    connMgrConfig.context != null ? connMgrConfig.context.getSocketFactory() : null;
            HostnameVerifier hostnameVerifier = connMgrConfig.verifier;
            if (ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT.equals(connMgrConfig.httpsSecurityMode)) {
                if (sslSocketFactory == null) {
                    sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                }
                if (hostnameVerifier == null) {
                    hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
                }
            } else if (ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(connMgrConfig.httpsSecurityMode)) {
                if (sslSocketFactory == null) {
                    try {
                        sslSocketFactory = new SSLContextBuilder()
                                .loadTrustMaterial(null, (chain, auth) -> true)
                                .build()
                                .getSocketFactory();
                    } catch (Exception e) {
                        throw new SSLInitializationException(
                                "Could not configure '" + connMgrConfig.httpsSecurityMode + "' HTTPS security mode", e);
                    }
                }
                if (hostnameVerifier == null) {
                    hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported '" + connMgrConfig.httpsSecurityMode + "' HTTPS security mode.");
            }

            registryBuilder.register(
                    "https",
                    new SSLConnectionSocketFactory(
                            sslSocketFactory, connMgrConfig.protocols, connMgrConfig.cipherSuites, hostnameVerifier));
        }

        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(
                new DefaultHttpClientConnectionOperator(
                        registryBuilder.build(), DefaultSchemePortResolver.INSTANCE, SystemDefaultDnsResolver.INSTANCE),
                ManagedHttpClientConnectionFactory.INSTANCE,
                connectionMaxTtlSeconds,
                TimeUnit.SECONDS);
        connMgr.setMaxTotal(maxConnectionsPerRoute * 2);
        connMgr.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        return connMgr;
    }

    public Object getUserToken(CompoundKey key) {
        return userTokens.get(key);
    }

    public void setUserToken(CompoundKey key, Object userToken) {
        if (userToken != null) {
            userTokens.put(key, userToken);
        } else {
            userTokens.remove(key);
        }
    }

    public Boolean getExpectContinue(CompoundKey key) {
        return expectContinues.get(key);
    }

    public void setExpectContinue(CompoundKey key, boolean enabled) {
        expectContinues.put(key, enabled);
    }
}
