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

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
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
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE_PATTERN;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_REDIRECTS;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.CONFIG_PROP_HTTP_RETRY_HANDLER_NAME;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.CONFIG_PROP_MAX_REDIRECTS;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.CONFIG_PROP_USE_SYSTEM_PROPERTIES;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.DEFAULT_FOLLOW_REDIRECTS;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.DEFAULT_MAX_REDIRECTS;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.DEFAULT_USE_SYSTEM_PROPERTIES;
import static org.eclipse.aether.transport.apache.ApacheTransporterConfigurationKeys.HTTP_RETRY_HANDLER_NAME_STANDARD;

/**
 * A transporter for HTTP/HTTPS.
 */
final class ApacheTransporter extends AbstractTransporter implements HttpTransporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheTransporter.class);

    private final ChecksumExtractor checksumExtractor;

    private final PathProcessor pathProcessor;

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final URI baseUri;

    private final HttpHost server;

    private final HttpHost proxy;

    private final CloseableHttpClient client;

    private final Map<?, ?> headers;

    private final LocalState state;

    private final boolean preemptiveAuth;

    private final boolean preemptivePutAuth;

    private final boolean supportWebDav;

    @SuppressWarnings("checkstyle:methodlength")
    ApacheTransporter(
            RemoteRepository repository,
            RepositorySystemSession session,
            ChecksumExtractor checksumExtractor,
            PathProcessor pathProcessor)
            throws NoTransporterException {
        this.checksumExtractor = checksumExtractor;
        this.pathProcessor = pathProcessor;
        try {
            this.baseUri = new URI(repository.getUrl()).parseServerAuthority();
            if (baseUri.isOpaque()) {
                throw new URISyntaxException(repository.getUrl(), "URL must not be opaque");
            }
            this.server = URIUtils.extractHost(baseUri);
            if (server == null) {
                throw new URISyntaxException(repository.getUrl(), "URL lacks host name");
            }
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }
        this.proxy = toHost(repository.getProxy());

        this.repoAuthContext = AuthenticationContext.forRepository(session, repository);
        this.proxyAuthContext = AuthenticationContext.forProxy(session, repository);

        String httpsSecurityMode = ConfigUtils.getString(
                session,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                ConfigurationProperties.HTTPS_SECURITY_MODE + "." + repository.getId(),
                ConfigurationProperties.HTTPS_SECURITY_MODE);
        final int connectionMaxTtlSeconds = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_CONNECTION_MAX_TTL,
                ConfigurationProperties.HTTP_CONNECTION_MAX_TTL + "." + repository.getId(),
                ConfigurationProperties.HTTP_CONNECTION_MAX_TTL);
        final int maxConnectionsPerRoute = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE,
                ConfigurationProperties.HTTP_MAX_CONNECTIONS_PER_ROUTE + "." + repository.getId(),
                ConfigurationProperties.HTTP_MAX_CONNECTIONS_PER_ROUTE);
        int connectTimeout = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.CONNECT_TIMEOUT);
        int requestTimeout = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT);

        SocketConfig socketConfig =
                // the time to establish connection (low level)
                SocketConfig.custom()
                        .setSoTimeout(requestTimeout, TimeUnit.MILLISECONDS)
                        .build();
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setTimeToLive(connectionMaxTtlSeconds, TimeUnit.SECONDS)
                .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setSocketTimeout(requestTimeout, TimeUnit.MILLISECONDS)
                .build();

        this.state = new LocalState(
                session,
                repository,
                new ConnMgrConfig(
                        session,
                        repoAuthContext,
                        httpsSecurityMode,
                        maxConnectionsPerRoute,
                        socketConfig,
                        connectionConfig));

        this.headers = ConfigUtils.getMap(
                session,
                Collections.emptyMap(),
                ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                ConfigurationProperties.HTTP_HEADERS);

        this.preemptiveAuth = ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_PREEMPTIVE_AUTH,
                ConfigurationProperties.HTTP_PREEMPTIVE_AUTH + "." + repository.getId(),
                ConfigurationProperties.HTTP_PREEMPTIVE_AUTH);
        this.preemptivePutAuth = ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_PREEMPTIVE_PUT_AUTH,
                ConfigurationProperties.HTTP_PREEMPTIVE_PUT_AUTH + "." + repository.getId(),
                ConfigurationProperties.HTTP_PREEMPTIVE_PUT_AUTH);
        this.supportWebDav = ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_SUPPORT_WEBDAV,
                ConfigurationProperties.HTTP_SUPPORT_WEBDAV + "." + repository.getId(),
                ConfigurationProperties.HTTP_SUPPORT_WEBDAV);
        String credentialEncoding = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "." + repository.getId(),
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING);
        int retryCount = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_COUNT,
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT);
        long retryInterval = ConfigUtils.getLong(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_INTERVAL,
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_INTERVAL);
        String serviceUnavailableCodesString = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE,
                ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE);
        String retryHandlerName = ConfigUtils.getString(
                session,
                HTTP_RETRY_HANDLER_NAME_STANDARD,
                CONFIG_PROP_HTTP_RETRY_HANDLER_NAME + "." + repository.getId(),
                CONFIG_PROP_HTTP_RETRY_HANDLER_NAME);
        int maxRedirects = ConfigUtils.getInteger(
                session,
                DEFAULT_MAX_REDIRECTS,
                CONFIG_PROP_MAX_REDIRECTS + "." + repository.getId(),
                CONFIG_PROP_MAX_REDIRECTS);
        boolean followRedirects = ConfigUtils.getBoolean(
                session,
                DEFAULT_FOLLOW_REDIRECTS,
                CONFIG_PROP_FOLLOW_REDIRECTS + "." + repository.getId(),
                CONFIG_PROP_FOLLOW_REDIRECTS);
        String userAgent = ConfigUtils.getString(
                session, ConfigurationProperties.DEFAULT_USER_AGENT, ConfigurationProperties.USER_AGENT);

        Charset credentialsCharset = Charset.forName(credentialEncoding);
        Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.BASIC, new BasicSchemeFactory(credentialsCharset))
                .register(StandardAuthScheme.DIGEST, new DigestSchemeFactory(credentialsCharset))
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setMaxRedirects(maxRedirects)
                .setRedirectsEnabled(followRedirects)
                .setConnectionRequestTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .setCookieSpec(StandardCookieSpec.STRICT)
                .build();

        Set<Integer> serviceUnavailableCodes = new HashSet<>();
        try {
            for (String code : ConfigUtils.parseCommaSeparatedUniqueNames(serviceUnavailableCodesString)) {
                serviceUnavailableCodes.add(Integer.parseInt(code));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Illegal HTTP codes for " + ConfigurationProperties.HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE
                            + " (list of integers): " + serviceUnavailableCodesString);
        }

        HttpRequestRetryStrategy retryHandler;
        if (HTTP_RETRY_HANDLER_NAME_STANDARD.equals(retryHandlerName)) {
            retryHandler = new ResolverServiceUnavailableRetryStrategy(
                    retryCount,
                    TimeValue.ofMilliseconds(retryInterval),
                    Arrays.asList(
                            InterruptedIOException.class,
                            UnknownHostException.class,
                            ConnectException.class,
                            ConnectionClosedException.class,
                            NoRouteToHostException.class,
                            SSLException.class),
                    serviceUnavailableCodes);
        } else {
            // TODO: no equivalent
            throw new IllegalArgumentException(
                    "Unsupported parameter " + CONFIG_PROP_HTTP_RETRY_HANDLER_NAME + " value: " + retryHandlerName);
        }

        HttpRoutePlanner routePlanner = null;
        InetAddress localAddress = getHttpLocalAddress(session, repository);
        if (localAddress != null) {
            if (proxy != null) {
                routePlanner = new DefaultProxyRoutePlanner(proxy, DefaultSchemePortResolver.INSTANCE) {
                    @Override
                    protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context) {
                        return localAddress;
                    }
                };
            } else {
                routePlanner = new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE) {
                    @Override
                    protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context) {
                        return localAddress;
                    }
                };
            }
        }

        HttpClientBuilder builder = HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(retryHandler)
                .setRoutePlanner(routePlanner)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setConnectionManager(state.getConnectionManager())
                .setConnectionManagerShared(true)
                .setDefaultCredentialsProvider(toCredentialsProvider(server, repoAuthContext, proxy, proxyAuthContext))
                .setProxy(proxy);
        final boolean useSystemProperties = ConfigUtils.getBoolean(
                session,
                DEFAULT_USE_SYSTEM_PROPERTIES,
                CONFIG_PROP_USE_SYSTEM_PROPERTIES + "." + repository.getId(),
                CONFIG_PROP_USE_SYSTEM_PROPERTIES);
        if (useSystemProperties) {
            LOGGER.warn(
                    "Transport used Apache HttpClient is instructed to use system properties: this may yield in unwanted side-effects!");
            LOGGER.warn("Please use documented means to configure resolver transport.");
            builder.useSystemProperties();
        }

        final String expectContinue = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_EXPECT_CONTINUE + "." + repository.getId(),
                ConfigurationProperties.HTTP_EXPECT_CONTINUE);
        if (expectContinue != null) {
            state.setExpectContinue(Boolean.parseBoolean(expectContinue));
        }

        final boolean reuseConnections = ConfigUtils.getBoolean(
                session,
                ConfigurationProperties.DEFAULT_HTTP_REUSE_CONNECTIONS,
                ConfigurationProperties.HTTP_REUSE_CONNECTIONS + "." + repository.getId(),
                ConfigurationProperties.HTTP_REUSE_CONNECTIONS);
        if (!reuseConnections) {
            builder.setConnectionReuseStrategy((request, response, context) -> false);
        }

        this.client = builder.build();
    }

    /**
     * Returns non-null {@link InetAddress} if set in configuration, {@code null} otherwise.
     */
    private InetAddress getHttpLocalAddress(RepositorySystemSession session, RemoteRepository repository) {
        String bindAddress = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_LOCAL_ADDRESS + "." + repository.getId(),
                ConfigurationProperties.HTTP_LOCAL_ADDRESS);
        if (bindAddress == null) {
            return null;
        }
        try {
            return InetAddress.getByName(bindAddress);
        } catch (UnknownHostException uhe) {
            throw new IllegalArgumentException(
                    "Given bind address (" + bindAddress + ") cannot be resolved for remote repository " + repository,
                    uhe);
        }
    }

    private static HttpHost toHost(Proxy proxy) {
        HttpHost host = null;
        if (proxy != null) {
            host = new HttpHost(proxy.getHost(), proxy.getPort());
        }
        return host;
    }

    private static CredentialsStore toCredentialsProvider(
            HttpHost server, AuthenticationContext serverAuthCtx, HttpHost proxy, AuthenticationContext proxyAuthCtx) {
        CredentialsStore provider = toCredentialsProvider(server.getHostName(), -1, serverAuthCtx);
        if (proxy != null) {
            CredentialsStore p = toCredentialsProvider(proxy.getHostName(), proxy.getPort(), proxyAuthCtx);
            provider = new DemuxCredentialsProvider(provider, p, proxy);
        }
        return provider;
    }

    private static CredentialsStore toCredentialsProvider(String host, int port, AuthenticationContext ctx) {
        DeferredCredentialsProvider provider = new DeferredCredentialsProvider();
        if (ctx != null) {
            AuthScope basicScope = new AuthScope(host, port);
            provider.setCredentials(basicScope, new DeferredCredentialsProvider.BasicFactory(ctx));
        }
        return provider;
    }

    LocalState getState() {
        return state;
    }

    private URI resolve(TransportTask task) {
        return UriUtils.resolve(baseUri, task.getLocation());
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof HttpTransporterException
                && ((HttpTransporterException) error).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        HttpHead request = commonHeaders(new HttpHead(resolve(task)));
        try {
            execute(request, null);
        } catch (HttpResponseException e) {
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = true;

        EntityGetter getter = new EntityGetter(task);
        HttpGet request = commonHeaders(new HttpGet(resolve(task)));
        while (true) {
            try {
                if (resume) {
                    resume(request, task);
                }
                execute(request, getter);
                break;
            } catch (HttpResponseException e) {
                if (resume
                        && e.getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED
                        && request.containsHeader(HttpHeaders.RANGE)) {
                    request = commonHeaders(new HttpGet(resolve(task)));
                    resume = false;
                    continue;
                }
                throw new HttpTransporterException(e.getStatusCode());
            }
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        PutTaskEntity entity = new PutTaskEntity(task);
        HttpPut request = commonHeaders(entity(new HttpPut(resolve(task)), entity));
        try {
            execute(request, null);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_EXPECTATION_FAILED && request.containsHeader(HttpHeaders.EXPECT)) {
                state.setExpectContinue(false);
                request = commonHeaders(entity(new HttpPut(request.getUri()), entity));
                execute(request, null);
                return;
            }
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    private void execute(HttpUriRequest request, EntityGetter getter) throws Exception {
        try {
            SharingHttpContext context = new SharingHttpContext(state);
            prepare(request, context);
            try (ClassicHttpResponse response = client.execute(server, request, context)) {
                try {
                    context.close();
                    handleStatus(response);
                    if (getter != null) {
                        getter.handle(response);
                    }
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            }
        } catch (IOException e) {
            if (e.getCause() instanceof TransferCancelledException) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private void prepare(HttpUriRequest request, SharingHttpContext context) throws Exception {
        final boolean put = HttpPut.METHOD_NAME.equalsIgnoreCase(request.getMethod());
        if (preemptiveAuth || (preemptivePutAuth && put)) {
            context.getAuthCache().put(server, new BasicScheme());
        }
        if (supportWebDav) {
            if (state.getWebDav() == null && (put || isPayloadPresent(request))) {
                HttpOptions req = commonHeaders(new HttpOptions(request.getUri()));
                try (ClassicHttpResponse response = client.execute(server, req, context)) {
                    state.setWebDav(response.containsHeader(HttpHeaders.DAV));
                    EntityUtils.consumeQuietly(response.getEntity());
                } catch (IOException e) {
                    LOGGER.debug("Failed to prepare HTTP context", e);
                }
            }
            if (put && Boolean.TRUE.equals(state.getWebDav())) {
                mkdirs(request.getUri(), context);
            }
        }
    }

    private void mkdirs(URI uri, SharingHttpContext context) throws Exception {
        List<URI> dirs = UriUtils.getDirectories(baseUri, uri);
        int index = 0;
        for (; index < dirs.size(); index++) {
            try (ClassicHttpResponse response =
                    client.execute(server, commonHeaders(new HttpMkCol(dirs.get(index))), context)) {
                try {
                    int status = response.getCode();
                    if (status < 300 || status == HttpStatus.SC_METHOD_NOT_ALLOWED) {
                        break;
                    } else if (status == HttpStatus.SC_CONFLICT) {
                        continue;
                    }
                    handleStatus(response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            } catch (IOException e) {
                LOGGER.debug("Failed to create parent directory {}", dirs.get(index), e);
                return;
            }
        }
        for (index--; index >= 0; index--) {
            try (ClassicHttpResponse response =
                    client.execute(server, commonHeaders(new HttpMkCol(dirs.get(index))), context)) {
                try {
                    handleStatus(response);
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            } catch (IOException e) {
                LOGGER.debug("Failed to create parent directory {}", dirs.get(index), e);
                return;
            }
        }
    }

    private <T extends HttpUriRequestBase> T entity(T request, HttpEntity entity) {
        request.setEntity(entity);
        return request;
    }

    private boolean isPayloadPresent(HttpUriRequest request) {
        HttpEntity entity = request.getEntity();
        return entity != null && entity.getContentLength() != 0;
    }

    private <T extends HttpUriRequest> T commonHeaders(T request) {
        request.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
        request.setHeader(HttpHeaders.PRAGMA, "no-cache");

        if (state.isExpectContinue() && isPayloadPresent(request)) {
            request.setHeader(HttpHeaders.EXPECT, "100-continue");
        }

        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                continue;
            }
            if (entry.getValue() instanceof String) {
                request.setHeader(entry.getKey().toString(), entry.getValue().toString());
            } else {
                request.removeHeaders(entry.getKey().toString());
            }
        }

        if (!state.isExpectContinue()) {
            request.removeHeaders(HttpHeaders.EXPECT);
        }

        return request;
    }

    private <T extends HttpUriRequest> void resume(T request, GetTask task) throws IOException {
        long resumeOffset = task.getResumeOffset();
        if (resumeOffset > 0L && task.getDataPath() != null) {
            long lastModified = Files.getLastModifiedTime(task.getDataPath()).toMillis();
            request.setHeader(HttpHeaders.RANGE, "bytes=" + resumeOffset + '-');
            request.setHeader(
                    HttpHeaders.IF_UNMODIFIED_SINCE,
                    DateUtils.formatStandardDate(Instant.ofEpochMilli(lastModified - 60L * 1000L)));
            request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
        }
    }

    private void handleStatus(ClassicHttpResponse response) throws Exception {
        int status = response.getCode();
        if (status >= 300) {
            ApacheRFC9457Reporter.INSTANCE.generateException(response, (statusCode, reasonPhrase) -> {
                throw new HttpResponseException(statusCode, reasonPhrase + " (" + statusCode + ")");
            });
        }
    }

    @Override
    protected void implClose() {
        try {
            client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        AuthenticationContext.close(repoAuthContext);
        AuthenticationContext.close(proxyAuthContext);
        state.close();
    }

    private class EntityGetter {

        private final GetTask task;

        EntityGetter(GetTask task) {
            this.task = task;
        }

        public void handle(ClassicHttpResponse response) throws IOException, TransferCancelledException {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                entity = new ByteArrayEntity(new byte[0], ContentType.DEFAULT_BINARY);
            }

            long offset = 0L, length = entity.getContentLength();
            Header rangeHeader = response.getFirstHeader(HttpHeaders.CONTENT_RANGE);
            String range = rangeHeader != null ? rangeHeader.getValue() : null;
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

            final boolean resume = offset > 0L;
            final Path dataFile = task.getDataPath();
            if (dataFile == null) {
                try (InputStream is = entity.getContent()) {
                    utilGet(task, is, true, length, resume);
                    extractChecksums(response);
                }
            } else {
                try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile)) {
                    task.setDataPath(tempFile.getPath(), resume);
                    if (resume && Files.isRegularFile(dataFile)) {
                        try (InputStream inputStream = Files.newInputStream(dataFile)) {
                            Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    try (InputStream is = entity.getContent()) {
                        utilGet(task, is, true, length, resume);
                    }
                    tempFile.move();
                } finally {
                    task.setDataPath(dataFile);
                }
            }
            if (task.getDataPath() != null) {
                Header lastModifiedHeader =
                        response.getFirstHeader(HttpHeaders.LAST_MODIFIED); // note: Wagon also does first not last
                if (lastModifiedHeader != null) {
                    Instant lastModified = DateUtils.parseStandardDate(lastModifiedHeader.getValue());
                    if (lastModified != null) {
                        pathProcessor.setLastModified(task.getDataPath(), lastModified.toEpochMilli());
                    }
                }
            }
            extractChecksums(response);
        }

        private void extractChecksums(ClassicHttpResponse response) {
            Map<String, String> checksums = checksumExtractor.extractChecksums(headerGetter(response));
            if (checksums != null && !checksums.isEmpty()) {
                checksums.forEach(task::setChecksum);
            }
        }
    }

    private static Function<String, String> headerGetter(ClassicHttpResponse closeableHttpResponse) {
        return s -> {
            Header header = closeableHttpResponse.getFirstHeader(s);
            return header != null ? header.getValue() : null;
        };
    }

    private class PutTaskEntity extends AbstractHttpEntity {

        private final PutTask task;

        PutTaskEntity(PutTask task) {
            super(ContentType.DEFAULT_BINARY, null, false);
            this.task = task;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public boolean isStreaming() {
            return false;
        }

        @Override
        public long getContentLength() {
            return task.getDataLength();
        }

        @Override
        public InputStream getContent() throws IOException {
            return task.newInputStream();
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            try {
                utilPut(task, os, false);
            } catch (TransferCancelledException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }

        @Override
        public void close() throws IOException {}
    }

    private static class ResolverServiceUnavailableRetryStrategy extends DefaultHttpRequestRetryStrategy {
        private ResolverServiceUnavailableRetryStrategy(
                int maxRetries,
                TimeValue defaultRetryInterval,
                Collection<Class<? extends IOException>> clazzes,
                Collection<Integer> codes) {
            super(maxRetries, defaultRetryInterval, clazzes, codes);
        }
    }
}
