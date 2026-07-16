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
package org.eclipse.aether.transport.url;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.aether.Keys;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpConstants;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;

/**
 * A special, "read only" and limited capability transport usable for bootstrapping. It provides HTTP with minimal
 * support (only basic auth, only GET/HEAD). It is implemented using {@link java.net.HttpURLConnection} class.
 *
 * @since 2.0.21
 */
public class UrlTransporter extends AbstractTransporter implements HttpTransporter {

    private static final int MAX_REDIRECTS = 5;
    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_PROXY_AUTHORIZATION = "Proxy-Authorization";
    private static final String AUTH_SCHEME_BASIC = "Basic";
    private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;

    private final ChecksumExtractor checksumExtractor;
    private final PathProcessor pathProcessor;
    private final URI baseUri;
    private final Map<String, String> headers;
    private final String userAgent;
    private final int connectTimeout;
    private final int requestTimeout;
    private final boolean preemptiveAuth;
    private final Charset authEncoding;
    private final String auth;
    private final Proxy proxy;
    private final String proxyAuth;

    private final Object authKey;
    private final Object proxyAuthKey;
    private final Function<Object, Boolean> cacheGetter;
    private final Consumer<Object> cacheSetter;

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    public UrlTransporter(
            RemoteRepository repository,
            RepositorySystemSession session,
            ChecksumExtractor checksumExtractor,
            PathProcessor pathProcessor)
            throws NoTransporterException {
        this.checksumExtractor = checksumExtractor;
        this.pathProcessor = pathProcessor;
        try {
            this.baseUri = HttpTransporterUtils.getBaseUri(repository);
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }

        this.headers = HttpTransporterUtils.getHttpHeaders(session, repository);
        this.userAgent = HttpTransporterUtils.getUserAgent(session, repository);
        this.connectTimeout = HttpTransporterUtils.getHttpConnectTimeout(session, repository);
        this.requestTimeout = HttpTransporterUtils.getHttpRequestTimeout(session, repository);
        String authString = null;
        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                String password = repoAuthContext.get(AuthenticationContext.PASSWORD);
                if (username != null && password != null) {
                    authString = username + ":" + password;
                }
            }
        }
        this.authEncoding = HttpTransporterUtils.getHttpCredentialsEncoding(session, repository);
        this.auth = authString;
        this.preemptiveAuth = this.auth != null && HttpTransporterUtils.isHttpPreemptiveAuth(session, repository);

        org.eclipse.aether.repository.Proxy repoProxy = repository.getProxy();
        this.proxy = repoProxy != null
                ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(repoProxy.getHost(), repoProxy.getPort()))
                : Proxy.NO_PROXY;
        String proxyAuthString = null;
        try (AuthenticationContext proxyAuthContext = AuthenticationContext.forProxy(session, repository)) {
            if (proxyAuthContext != null) {
                String username = proxyAuthContext.get(AuthenticationContext.USERNAME);
                String password = proxyAuthContext.get(AuthenticationContext.PASSWORD);
                if (username != null && password != null) {
                    proxyAuthString = username + ":" + password;
                }
            }
        }
        this.proxyAuth = proxyAuthString;

        this.authKey = Keys.of(UrlTransporter.class, repository, "auth");
        this.proxyAuthKey = Keys.of(UrlTransporter.class, repository, "proxyAuth");
        if (session.getCache() != null) {
            this.cacheGetter = k -> {
                Boolean ret = (Boolean) session.getCache().get(session, k);
                if (ret == null) {
                    return false;
                } else {
                    return ret;
                }
            };
            this.cacheSetter = k -> session.getCache().put(session, k, Boolean.TRUE);
        } else {
            this.cacheGetter = k -> false;
            this.cacheSetter = k -> {};
        }
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        HttpURLConnection con = perform(METHOD_HEAD, baseUri.resolve(task.getLocation()), null);
        try {
            int responseCode = con.getResponseCode();
            if (HttpURLConnection.HTTP_OK != responseCode) {
                throw new HttpTransporterException(responseCode);
            }
        } finally {
            con.disconnect();
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        HttpURLConnection con = perform(METHOD_GET, baseUri.resolve(task.getLocation()), task);
        try {
            int responseCode = con.getResponseCode();
            if (HttpURLConnection.HTTP_OK != responseCode) {
                throw new HttpTransporterException(responseCode);
            }
            IOSupplier<InputStream> inputStreamSupplier = () -> {
                String contentEncoding = con.getHeaderField("Content-Encoding");
                if (contentEncoding != null) {
                    if ("gzip".equalsIgnoreCase(contentEncoding)) {
                        return new GZIPInputStream(con.getInputStream());
                    } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
                        return new InflaterInputStream(con.getInputStream());
                    }
                }
                return con.getInputStream();
            };
            final Path dataFile = task.getDataPath();
            if (dataFile == null) {
                try (InputStream is = inputStreamSupplier.get()) {
                    utilGet(task, is, true, con.getContentLengthLong(), false);
                }
            } else {
                try (PathProcessor.CollocatedTempFile tempFile = pathProcessor.newTempFile(dataFile)) {
                    task.setDataPath(tempFile.getPath(), false);
                    try (InputStream is = inputStreamSupplier.get()) {
                        utilGet(task, is, true, con.getContentLengthLong(), false);
                    }
                    tempFile.move();
                } finally {
                    task.setDataPath(dataFile);
                }
            }
            if (task.getDataPath() != null) {
                long lastModified = con.getLastModified();
                if (lastModified != 0) {
                    pathProcessor.setLastModified(task.getDataPath(), lastModified);
                }
            }
        } finally {
            con.disconnect();
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        throw new IOException("unsupported operation");
    }

    @Override
    protected void implClose() {
        // nothing
    }

    private HttpURLConnection perform(String method, URI target, GetTask task) throws IOException {
        String currAuth = preemptiveAuth ? auth : null;
        String currProxyAuth = null;
        if (cacheGetter.apply(authKey)) {
            currAuth = this.auth;
        }
        if (cacheGetter.apply(proxyAuthKey)) {
            currProxyAuth = this.proxyAuth;
        }
        return perform(method, new ArrayList<>(Collections.singletonList(target)), currAuth, currProxyAuth, task);
    }

    private HttpURLConnection perform(
            String method, ArrayList<URI> target, String currAuth, String currProxyAuth, GetTask task)
            throws IOException {
        if (target.size() > MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }
        HttpURLConnection con = (HttpURLConnection) target.get(0).toURL().openConnection(proxy);
        con.setConnectTimeout(connectTimeout);
        con.setReadTimeout(requestTimeout);
        con.setRequestMethod(method);
        con.setUseCaches(false);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty(HttpConstants.ACCEPT_ENCODING, "gzip,deflate");
        con.setRequestProperty(HttpConstants.CACHE_CONTROL, "no-cache, no-store");
        con.setRequestProperty("Pragma", "no-cache");
        con.setRequestProperty(HttpConstants.USER_AGENT, userAgent);
        headers.forEach(con::setRequestProperty);
        if (currAuth != null) {
            con.setRequestProperty(HEADER_AUTHORIZATION, basicAuthorization(currAuth));
        }
        if (currProxyAuth != null) {
            con.setRequestProperty(HEADER_PROXY_AUTHORIZATION, basicAuthorization(currProxyAuth));
        }
        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            if (task != null) {
                Map<String, String> checksums = checksumExtractor.extractChecksums(con::getHeaderField);
                if (checksums != null && !checksums.isEmpty()) {
                    checksums.forEach(task::setChecksum);
                }
            }
        } else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == HTTP_STATUS_TEMPORARY_REDIRECT
                || responseCode == HTTP_STATUS_PERMANENT_REDIRECT) {
            String location = con.getHeaderField(HEADER_LOCATION);
            if (location == null) {
                con.disconnect();
                throw new IOException("Redirect response missing Location header");
            }
            URI currentUri = URI.create(con.getURL().toString());
            URI redirectUri = currentUri.resolve(location);
            // forbid HTTPS -> HTTP downgrade during redirect
            if ("https".equalsIgnoreCase(currentUri.getScheme()) && "http".equalsIgnoreCase(redirectUri.getScheme())) {
                con.disconnect();
                throw new IOException("Refusing to follow redirect from https to http");
            }
            // ensure we are HTTP or HTTPS after redirect
            if (!"http".equalsIgnoreCase(redirectUri.getScheme())
                    && !"https".equalsIgnoreCase(redirectUri.getScheme())) {
                con.disconnect();
                throw new IOException("Unsupported redirect protocol: " + redirectUri.getScheme());
            }
            // reset auth if authority differs after redirect
            String currentAuthority = currentUri.getAuthority();
            String redirectAuthority = redirectUri.getAuthority();
            if (currentAuthority == null || !currentAuthority.equalsIgnoreCase(redirectAuthority)) {
                currAuth = null;
            }
            target.add(0, redirectUri);
            con.disconnect();
            return perform(method, target, currAuth, currProxyAuth, task);
        } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && currAuth == null && this.auth != null) {
            con.disconnect();
            return perform(method, target, this.auth, currProxyAuth, task);
        } else if (responseCode == HttpURLConnection.HTTP_PROXY_AUTH
                && currProxyAuth == null
                && this.proxyAuth != null) {
            con.disconnect();
            return perform(method, target, currAuth, this.proxyAuth, task);
        }
        if (currAuth != null) {
            cacheSetter.accept(authKey);
        }
        if (currProxyAuth != null) {
            cacheSetter.accept(proxyAuthKey);
        }
        return con;
    }

    private String basicAuthorization(String credentials) {
        return AUTH_SCHEME_BASIC + " " + Base64.getEncoder().encodeToString(credentials.getBytes(authEncoding));
    }
}
