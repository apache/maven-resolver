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

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;
import org.eclipse.jetty.client.Authentication;
import org.eclipse.jetty.client.BasicAuthentication;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.InputStreamResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpClientConnectionFactory;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.ClientConnectionFactoryOverHTTP2;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.ACCEPT_ENCODING;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_LENGTH;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE_PATTERN;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.IF_UNMODIFIED_SINCE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.LAST_MODIFIED;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.MULTIPLE_CHOICES;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.PRECONDITION_FAILED;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.RANGE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.USER_AGENT;

/**
 * A transporter for HTTP/HTTPS.
 *
 * @since 2.0.0
 */
final class JettyTransporter extends AbstractTransporter implements HttpTransporter {
    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    private final RepositorySystemSession session;

    private final RemoteRepository repository;

    private final ChecksumExtractor checksumExtractor;

    private final PathProcessor pathProcessor;

    private final URI baseUri;

    private final HttpClient client;

    private final int connectTimeout;

    private final int requestTimeout;

    private final Map<String, String> headers;

    private final boolean preemptiveAuth;

    private final boolean preemptivePutAuth;

    private final boolean insecure;

    private final AtomicReference<BasicAuthentication.BasicResult> basicServerAuthenticationResult;

    private final AtomicReference<BasicAuthentication.BasicResult> basicProxyAuthenticationResult;

    JettyTransporter(
            RepositorySystemSession session,
            RemoteRepository repository,
            ChecksumExtractor checksumExtractor,
            PathProcessor pathProcessor)
            throws NoTransporterException {
        this.session = session;
        this.repository = repository;
        this.checksumExtractor = checksumExtractor;
        this.pathProcessor = pathProcessor;
        try {
            URI uri = new URI(repository.getUrl()).parseServerAuthority();
            if (uri.isOpaque()) {
                throw new URISyntaxException(repository.getUrl(), "URL must not be opaque");
            }
            if (uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw new URISyntaxException(repository.getUrl(), "URL must not have fragment or query");
            }
            String path = uri.getPath();
            if (path == null) {
                path = "/";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            this.baseUri = URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path);
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }

        HashMap<String, String> headers = new HashMap<>();
        String userAgent = HttpTransporterUtils.getUserAgent(session, repository);
        if (userAgent != null) {
            headers.put(USER_AGENT, userAgent);
        }
        Map<String, String> configuredHeaders = HttpTransporterUtils.getHttpHeaders(session, repository);
        if (configuredHeaders != null) {
            headers.putAll(configuredHeaders);
        }

        this.headers = headers;

        this.connectTimeout = HttpTransporterUtils.getHttpRequestTimeout(session, repository);
        this.requestTimeout = HttpTransporterUtils.getHttpRequestTimeout(session, repository);
        this.preemptiveAuth = HttpTransporterUtils.isHttpPreemptiveAuth(session, repository);
        this.preemptivePutAuth = HttpTransporterUtils.isHttpPreemptivePutAuth(session, repository);
        final String httpsSecurityMode = HttpTransporterUtils.getHttpsSecurityMode(session, repository);
        this.insecure = ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode);

        this.basicServerAuthenticationResult = new AtomicReference<>(null);
        this.basicProxyAuthenticationResult = new AtomicReference<>(null);
        this.client = createClient();
    }

    private void mayApplyPreemptiveAuth(Request request) {
        if (basicServerAuthenticationResult.get() != null) {
            basicServerAuthenticationResult.get().apply(request);
        }
        if (basicProxyAuthenticationResult.get() != null) {
            basicProxyAuthenticationResult.get().apply(request);
        }
    }

    private URI resolve(TransportTask task) {
        return baseUri.resolve(task.getLocation());
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        Request request = client.newRequest(resolve(task)).method("HEAD");
        request.headers(m -> headers.forEach(m::add));
        if (preemptiveAuth) {
            mayApplyPreemptiveAuth(request);
        }
        Response response = request.send();
        if (response.getStatus() >= MULTIPLE_CHOICES) {
            throw new HttpTransporterException(response.getStatus());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = task.getResumeOffset() > 0L && task.getDataPath() != null;
        Response response;
        InputStreamResponseListener listener;

        while (true) {
            Request request = client.newRequest(resolve(task)).method("GET");
            request.headers(m -> headers.forEach(m::add));
            if (preemptiveAuth) {
                mayApplyPreemptiveAuth(request);
            }

            if (resume) {
                long resumeOffset = task.getResumeOffset();
                long lastModified =
                        Files.getLastModifiedTime(task.getDataPath()).toMillis();
                request.headers(h -> {
                    h.add(RANGE, "bytes=" + resumeOffset + '-');
                    h.addDateField(IF_UNMODIFIED_SINCE, lastModified - MODIFICATION_THRESHOLD);
                    h.remove(HttpHeader.ACCEPT_ENCODING);
                    h.add(ACCEPT_ENCODING, "identity");
                });
            }

            listener = new InputStreamResponseListener();
            request.send(listener);
            try {
                response = listener.get(requestTimeout, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof Exception) {
                    throw (Exception) t;
                } else {
                    throw new RuntimeException(t);
                }
            }
            if (response.getStatus() >= MULTIPLE_CHOICES) {
                if (resume && response.getStatus() == PRECONDITION_FAILED) {
                    resume = false;
                    continue;
                }
                JettyRFC9457Reporter.INSTANCE.generateException(listener, (statusCode, reasonPhrase) -> {
                    throw new HttpTransporterException(statusCode);
                });
            }
            break;
        }

        long offset = 0L, length = response.getHeaders().getLongField(CONTENT_LENGTH);
        if (resume) {
            String range = response.getHeaders().get(CONTENT_RANGE);
            if (range != null) {
                Matcher m = CONTENT_RANGE_PATTERN.matcher(range);
                if (!m.matches()) {
                    throw new IOException("Invalid Content-Range header for partial download: " + range);
                }
                offset = Long.parseLong(m.group(1));
                length = Long.parseLong(m.group(2)) + 1L;
                if (offset < 0L || offset >= length || (offset > 0L && offset != task.getResumeOffset())) {
                    throw new IOException("Invalid Content-Range header for partial download from offset "
                            + task.getResumeOffset() + ": " + range);
                }
            }
        }

        final boolean downloadResumed = offset > 0L;
        final Path dataFile = task.getDataPath();
        if (dataFile == null) {
            try (InputStream is = listener.getInputStream()) {
                utilGet(task, is, true, length, downloadResumed);
            }
        } else {
            try (PathProcessor.CollocatedTempFile tempFile = pathProcessor.newTempFile(dataFile)) {
                task.setDataPath(tempFile.getPath(), downloadResumed);
                if (downloadResumed && Files.isRegularFile(dataFile)) {
                    try (InputStream inputStream = Files.newInputStream(dataFile)) {
                        Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                try (InputStream is = listener.getInputStream()) {
                    utilGet(task, is, true, length, downloadResumed);
                }
                tempFile.move();
            } finally {
                task.setDataPath(dataFile);
            }
        }
        if (task.getDataPath() != null && response.getHeaders().getDateField(LAST_MODIFIED) != -1) {
            long lastModified =
                    response.getHeaders().getDateField(LAST_MODIFIED); // note: Wagon also does first not last
            if (lastModified != -1) {
                pathProcessor.setLastModified(task.getDataPath(), lastModified);
            }
        }
        Map<String, String> checksums = checksumExtractor.extractChecksums(headerGetter(response));
        if (checksums != null && !checksums.isEmpty()) {
            checksums.forEach(task::setChecksum);
        }
    }

    private static Function<String, String> headerGetter(Response response) {
        return s -> response.getHeaders().get(s);
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        Request request = client.newRequest(resolve(task)).method("PUT");
        request.headers(m -> headers.forEach(m::add));
        if (preemptiveAuth || preemptivePutAuth) {
            mayApplyPreemptiveAuth(request);
        }
        request.body(PutTaskRequestContent.from(task));
        AtomicBoolean started = new AtomicBoolean(false);
        Response response;
        try {
            response = request.onRequestCommit(r -> {
                        if (task.getDataLength() == 0) {
                            if (started.compareAndSet(false, true)) {
                                try {
                                    task.getListener().transportStarted(0, task.getDataLength());
                                } catch (TransferCancelledException e) {
                                    r.abort(e);
                                }
                            }
                        }
                    })
                    .onRequestContent((r, b) -> {
                        if (started.compareAndSet(false, true)) {
                            try {
                                task.getListener().transportStarted(0, task.getDataLength());
                            } catch (TransferCancelledException e) {
                                r.abort(e);
                                return;
                            }
                        }
                        try {
                            task.getListener().transportProgressed(b);
                        } catch (TransferCancelledException e) {
                            r.abort(e);
                        }
                    })
                    .send();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException ioex) {
                if (ioex.getCause() instanceof TransferCancelledException) {
                    throw (TransferCancelledException) ioex.getCause();
                } else {
                    throw ioex;
                }
            } else if (t instanceof Exception) {
                throw (Exception) t;
            } else {
                throw new RuntimeException(t);
            }
        }
        if (response.getStatus() >= MULTIPLE_CHOICES) {
            throw new HttpTransporterException(response.getStatus());
        }
    }

    @Override
    protected void implClose() {
        try {
            this.client.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("checkstyle:methodlength")
    private HttpClient createClient() throws RuntimeException {
        BasicAuthentication.BasicResult serverAuth = null;
        BasicAuthentication.BasicResult proxyAuth = null;
        SSLContext sslContext = null;
        BasicAuthentication basicAuthentication = null;
        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                sslContext = repoAuthContext.get(AuthenticationContext.SSL_CONTEXT, SSLContext.class);

                String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                String password = repoAuthContext.get(AuthenticationContext.PASSWORD);

                URI uri = URI.create(repository.getUrl());
                basicAuthentication = new BasicAuthentication(uri, Authentication.ANY_REALM, username, password);
                if (preemptiveAuth || preemptivePutAuth) {
                    serverAuth = new BasicAuthentication.BasicResult(uri, HttpHeader.AUTHORIZATION, username, password);
                }
            }
        }

        if (sslContext == null) {
            try {
                if (insecure) {
                    sslContext = SSLContext.getInstance("TLS");
                    X509TrustManager tm = new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    };
                    sslContext.init(null, new X509TrustManager[] {tm}, null);
                } else {
                    sslContext = SSLContext.getDefault();
                }
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new IllegalStateException("SSL Context setup failure", e);
                }
            }
        }

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(sslContext);
        if (insecure) {
            sslContextFactory.setEndpointIdentificationAlgorithm(null);
            sslContextFactory.setHostnameVerifier((name, context) -> true);
        }

        ClientConnector clientConnector = new ClientConnector();
        clientConnector.setSslContextFactory(sslContextFactory);

        HTTP2Client http2Client = new HTTP2Client(clientConnector);
        ClientConnectionFactoryOverHTTP2.HTTP2 http2 = new ClientConnectionFactoryOverHTTP2.HTTP2(http2Client);

        HttpClientTransportDynamic transport;
        if ("https".equalsIgnoreCase(repository.getProtocol())) {
            transport = new HttpClientTransportDynamic(
                    clientConnector, http2, HttpClientConnectionFactory.HTTP11); // HTTPS, prefer H2
        } else {
            transport = new HttpClientTransportDynamic(
                    clientConnector, HttpClientConnectionFactory.HTTP11, http2); // plaintext HTTP, H2 cannot be used
        }

        HttpClient httpClient = new HttpClient(transport);
        httpClient.setConnectTimeout(connectTimeout);
        httpClient.setIdleTimeout(requestTimeout);
        httpClient.setFollowRedirects(ConfigUtils.getBoolean(
                session,
                JettyTransporterConfigurationKeys.DEFAULT_FOLLOW_REDIRECTS,
                JettyTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_REDIRECTS));
        httpClient.setMaxRedirects(ConfigUtils.getInteger(
                session,
                JettyTransporterConfigurationKeys.DEFAULT_MAX_REDIRECTS,
                JettyTransporterConfigurationKeys.CONFIG_PROP_MAX_REDIRECTS));

        httpClient.setUserAgentField(null); // we manage it

        if (basicAuthentication != null) {
            httpClient.getAuthenticationStore().addAuthentication(basicAuthentication);
        }

        if (repository.getProxy() != null) {
            HttpProxy proxy = new HttpProxy(
                    repository.getProxy().getHost(), repository.getProxy().getPort());

            httpClient.getProxyConfiguration().addProxy(proxy);
            try (AuthenticationContext proxyAuthContext = AuthenticationContext.forProxy(session, repository)) {
                if (proxyAuthContext != null) {
                    String username = proxyAuthContext.get(AuthenticationContext.USERNAME);
                    String password = proxyAuthContext.get(AuthenticationContext.PASSWORD);

                    BasicAuthentication proxyAuthentication =
                            new BasicAuthentication(proxy.getURI(), Authentication.ANY_REALM, username, password);

                    httpClient.getAuthenticationStore().addAuthentication(proxyAuthentication);
                    if (preemptiveAuth || preemptivePutAuth) {
                        proxyAuth = new BasicAuthentication.BasicResult(
                                proxy.getURI(), HttpHeader.PROXY_AUTHORIZATION, username, password);
                    }
                }
            }
        }
        if (serverAuth != null) {
            this.basicServerAuthenticationResult.set(serverAuth);
        }
        if (proxyAuth != null) {
            this.basicProxyAuthenticationResult.set(proxyAuth);
        }

        try {
            httpClient.start();
            return httpClient;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new IllegalStateException("Jetty client start failure", e);
            }
        }
    }
}
