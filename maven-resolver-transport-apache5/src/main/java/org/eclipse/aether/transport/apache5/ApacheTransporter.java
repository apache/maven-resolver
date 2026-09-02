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
package org.eclipse.aether.transport.apache5;

import javax.net.ssl.SSLSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.KerberosSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.aether.Keys;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpTransportPropertiesBuilder;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.HttpTransportProperty.HttpVersion;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.StringDigestUtil;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE_PATTERN;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_FOLLOW_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_HTTP_RETRY_HANDLER_NAME;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_MAX_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.CONFIG_PROP_USE_SYSTEM_PROPERTIES;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.DEFAULT_FOLLOW_INSECURE_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.DEFAULT_FOLLOW_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.DEFAULT_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.DEFAULT_MAX_REDIRECTS;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.DEFAULT_USE_SYSTEM_PROPERTIES;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.HTTP_RETRY_HANDLER_NAME_DEFAULT;
import static org.eclipse.aether.transport.apache5.ApacheTransporterConfigurationKeys.HTTP_RETRY_HANDLER_NAME_STANDARD;

/**
 * A transporter for HTTP/HTTPS based on Apache HttpClient 5.x.
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

    private final boolean sendRfc9457Accept;

    private final CredentialsProvider credentialsProvider;

    private final RequestConfig requestConfig;

    private final AuthCache authCache;

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
            this.baseUri = HttpTransporterUtils.getBaseUri(repository);
            HttpHost rawServer = URIUtils.extractHost(baseUri);
            if (rawServer == null) {
                throw new URISyntaxException(repository.getUrl(), "URL lacks host name");
            }
            this.server = new HttpHost(rawServer.getSchemeName(), rawServer.getHostName(), effectivePort(rawServer));
        } catch (URISyntaxException e) {
            throw new NoTransporterException(repository, e.getMessage(), e);
        }
        this.proxy = toHost(repository.getProxy());

        this.repoAuthContext = AuthenticationContext.forRepository(session, repository);
        this.proxyAuthContext = AuthenticationContext.forProxy(session, repository);

        String httpsSecurityMode = HttpTransporterUtils.getHttpsSecurityMode(session, repository);
        final int connectionMaxTtlSeconds = HttpTransporterUtils.getHttpConnectionMaxTtlSeconds(session, repository);
        final int maxConnectionsPerRoute = HttpTransporterUtils.getHttpMaxConnectionsPerRoute(session, repository);
        this.state = new LocalState(
                session,
                repository,
                new ConnMgrConfig(
                        session, repoAuthContext, httpsSecurityMode, connectionMaxTtlSeconds, maxConnectionsPerRoute));

        this.headers = HttpTransporterUtils.getHttpHeaders(session, repository);
        this.preemptiveAuth = HttpTransporterUtils.isHttpPreemptiveAuth(session, repository);
        this.preemptivePutAuth = HttpTransporterUtils.isHttpPreemptivePutAuth(session, repository);
        this.supportWebDav = HttpTransporterUtils.isHttpSupportWebDav(session, repository);
        this.sendRfc9457Accept = HttpTransporterUtils.isHttpSendRfc9457Accept(session, repository);
        int connectTimeout = HttpTransporterUtils.getHttpConnectTimeout(session, repository);
        int requestTimeout = HttpTransporterUtils.getHttpRequestTimeout(session, repository);
        int retryCount = HttpTransporterUtils.getHttpRetryHandlerCount(session, repository);
        long retryInterval = HttpTransporterUtils.getHttpRetryHandlerInterval(session, repository);
        long retryIntervalMax = HttpTransporterUtils.getHttpRetryHandlerIntervalMax(session, repository);
        String retryHandlerName = ConfigUtils.getString(
                session,
                HTTP_RETRY_HANDLER_NAME_STANDARD,
                CONFIG_PROP_HTTP_RETRY_HANDLER_NAME + "." + repository.getId(),
                CONFIG_PROP_HTTP_RETRY_HANDLER_NAME);
        boolean retryHandlerRequestSentEnabled = ConfigUtils.getBoolean(
                session,
                DEFAULT_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED,
                CONFIG_PROP_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED + "." + repository.getId(),
                CONFIG_PROP_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED);
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
        boolean followInsecureRedirects = ConfigUtils.getBoolean(
                session,
                DEFAULT_FOLLOW_INSECURE_REDIRECTS,
                CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS + "." + repository.getId(),
                CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS);
        String userAgent = HttpTransporterUtils.getUserAgent(session, repository);

        // NOTE: aether.transport.http.credentialEncoding has no effect with this (Apache HttpClient 5) transport.
        // BasicSchemeFactory/DigestSchemeFactory in httpclient5 5.4.x never pass their configured charset on to the
        // BasicScheme/DigestScheme they create (verified against the 5.4.2 bytecode: create() calls the no-arg
        // constructor), so the scheme always negotiates its own charset (UTF-8 for Basic, absent a server-supplied
        // "charset" auth-param) regardless of what is configured here. Kept for config-key compatibility only.
        Lookup<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.NTLM, new NTLMSchemeFactory())
                .register(StandardAuthScheme.SPNEGO, SPNegoSchemeFactory.DEFAULT)
                .register(StandardAuthScheme.KERBEROS, KerberosSchemeFactory.DEFAULT)
                .build();

        this.requestConfig = RequestConfig.custom()
                .setMaxRedirects(maxRedirects)
                .setRedirectsEnabled(followRedirects)
                .setResponseTimeout(Timeout.ofMilliseconds(requestTimeout))
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setExpectContinueEnabled(true)
                .setCookieSpec(StandardCookieSpec.RELAXED)
                .build();

        HttpRequestRetryStrategy retryStrategy = new ResolverHttpRequestRetryStrategy(
                retryCount,
                retryInterval,
                retryIntervalMax,
                HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository),
                retryHandlerName,
                retryHandlerRequestSentEnabled);

        this.credentialsProvider = toCredentialsProvider(server, repoAuthContext, proxy, proxyAuthContext);
        HttpClientBuilder builder = HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setRedirectStrategy(new ResolverRedirectStrategy(followInsecureRedirects))
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(retryStrategy)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setConnectionManager(state.getConnectionManager())
                .setConnectionManagerShared(true)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(proxy);

        if (ConfigUtils.getBoolean(
                session,
                ApacheTransporterConfigurationKeys.DEFAULT_ORIGIN_SCOPED_HEADERS,
                ApacheTransporterConfigurationKeys.CONFIG_PROP_ORIGIN_SCOPED_HEADERS + "." + repository.getId(),
                ApacheTransporterConfigurationKeys.CONFIG_PROP_ORIGIN_SCOPED_HEADERS)) {
            // Configured headers are per-repository data and frequently carry credentials; scope them to the
            // repository origin so a cross-origin redirect hop does not replay them to the redirect target.
            // Challenge-based credentials are host-scoped by the credentials provider already.
            builder.addRequestInterceptorLast(new OriginScopedHeadersInterceptor(server, this.headers.keySet()));
        }
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

        HttpTransporterUtils.getHttpLocalAddress(session, repository).ifPresent(localAddress -> {
            // HttpClientBuilder.build() short-circuits ALL of its own route-planner selection - including the
            // useSystemProperties()-driven SystemDefaultRoutePlanner branch that honors -Dhttp.proxyHost etc. - the
            // moment an explicit planner is installed via setRoutePlanner(). Reproduce whichever planner the
            // builder would otherwise have chosen and only override determineLocalAddress(...), so configuring a
            // local bind address does not silently disable system-property/proxy routing.
            // Same precedence as HttpClientBuilder.build(): an explicitly configured proxy wins over system
            // properties, so a repository proxy from the settings is not dropped when both are present.
            if (proxy != null) {
                builder.setRoutePlanner(new DefaultProxyRoutePlanner(proxy) {
                    @Override
                    protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context)
                            throws HttpException {
                        return localAddress;
                    }
                });
            } else if (useSystemProperties) {
                builder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()) {
                    @Override
                    protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context)
                            throws HttpException {
                        return localAddress;
                    }
                });
            } else {
                builder.setRoutePlanner(new DefaultRoutePlanner(null) {
                    @Override
                    protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context)
                            throws HttpException {
                        return localAddress;
                    }
                });
            }
        });

        HttpTransporterUtils.getHttpExpectContinue(session, repository).ifPresent(state::setExpectContinue);
        if (!HttpTransporterUtils.isHttpReuseConnections(session, repository)) {
            builder.setConnectionReuseStrategy((request, response, context) -> false);
        }

        if (session.getCache() != null) {
            this.authCache = (AuthCache) session.getCache()
                    .computeIfAbsent(
                            session,
                            Keys.of(
                                    getClass(),
                                    repository.getId() + "-" + StringDigestUtil.sha1(repository.toString())),
                            BasicAuthCache::new);
        } else {
            this.authCache = new BasicAuthCache();
        }
        this.client = builder.build();
    }

    private static HttpHost toHost(Proxy proxy) {
        HttpHost host = null;
        if (proxy != null) {
            // Proxy.getType() denotes the repository protocol this proxy was selected for, not the protocol used to
            // talk to the proxy itself: the connection to a forward proxy is plain HTTP even when the repository
            // behind it is HTTPS. Using proxy.getType() as the HttpHost scheme would make HttpClient attempt a TLS
            // handshake with a plain-HTTP proxy whenever <proxy><protocol>https</protocol> is set in settings.xml.
            // See apache/maven#2519 and maven-resolver#745.
            HttpHost plainHost = new HttpHost(proxy.getHost(), proxy.getPort());
            host = new HttpHost(plainHost.getHostName(), effectivePort(plainHost));
        }
        return host;
    }

    private static CredentialsProvider toCredentialsProvider(
            HttpHost server, AuthenticationContext serverAuthCtx, HttpHost proxy, AuthenticationContext proxyAuthCtx) {
        CredentialsProvider provider = toCredentialsProvider(server, serverAuthCtx);
        if (proxy != null) {
            CredentialsProvider p = toCredentialsProvider(proxy, proxyAuthCtx);
            provider = new DemuxCredentialsProvider(provider, p, proxy);
        }
        return provider;
    }

    static int effectivePort(HttpHost host) {
        if (host.getPort() >= 0) {
            return host.getPort();
        }
        return "https".equalsIgnoreCase(host.getSchemeName()) ? 443 : 80;
    }

    private static CredentialsProvider toCredentialsProvider(HttpHost host, AuthenticationContext ctx) {
        DeferredCredentialsProvider provider = new DeferredCredentialsProvider();
        if (ctx != null) {
            AuthScope basicScope = new AuthScope(host, null, null);
            provider.setCredentials(basicScope, new DeferredCredentialsProvider.BasicFactory(ctx));

            AuthScope ntlmScope = new AuthScope(host, null, "ntlm");
            provider.setCredentials(ntlmScope, new DeferredCredentialsProvider.NtlmFactory(ctx));
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
    protected void implPeek(PeekTask task) throws Exception {
        HttpHead request = commonHeaders(new HttpHead(resolve(task)));
        try {
            execute(request, null, task.getListener());
        } catch (HttpResponseException e) {
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = true;

        EntityGetter getter = new EntityGetter(task);
        HttpGet request = commonHeaders(new HttpGet(resolve(task)));
        if (sendRfc9457Accept) {
            ApacheRFC9457Reporter.INSTANCE.prepareRequest(request);
        }
        while (true) {
            try {
                if (resume) {
                    resume(request, task);
                }
                execute(request, getter, task.getListener());
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
        if (sendRfc9457Accept) {
            ApacheRFC9457Reporter.INSTANCE.prepareRequest(request);
        }
        try {
            execute(request, null, task.getListener());
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_EXPECTATION_FAILED && request.containsHeader(HttpHeaders.EXPECT)) {
                state.setExpectContinue(false);
                HttpPut retryRequest = new HttpPut(request.getUri());
                request = commonHeaders(entity(retryRequest, entity));
                execute(request, null, task.getListener());
                return;
            }
            throw new HttpTransporterException(e.getStatusCode());
        }
    }

    private void execute(ClassicHttpRequest request, EntityGetter getter, TransportListener listener) throws Exception {
        try {
            SharingHttpContext context = new SharingHttpContext(state);
            RequestConfig config = requestConfig;
            Boolean expectContinue = state.isExpectContinue();
            if (expectContinue != null && expectContinue != config.isExpectContinueEnabled()) {
                config = RequestConfig.copy(config)
                        .setExpectContinueEnabled(expectContinue)
                        .build();
            }
            context.setRequestConfig(config);
            context.setCredentialsProvider(credentialsProvider);
            context.setAuthCache(authCache);
            prepare(request, context);
            try (CloseableHttpResponse response = client.execute(server, request, context)) {
                try {
                    Map<TransferEvent.TransportPropertyKey, Object> transportProperties =
                            createTransportProperties(response, context);
                    listener.transportPropertiesAvailable(transportProperties);
                    handleStatus(response);
                    if (getter != null) {
                        getter.handle(response);
                    }
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            }
            Object userToken = context.getUserToken();
            if (userToken != null) {
                state.setUserToken(userToken);
            }
        } catch (IOException e) {
            if (e.getCause() instanceof TransferCancelledException) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private void prepare(ClassicHttpRequest request, SharingHttpContext context) throws Exception {
        final boolean put = HttpPut.METHOD_NAME.equalsIgnoreCase(request.getMethod());
        if (preemptiveAuth || (preemptivePutAuth && put)) {
            Credentials credentials = credentialsProvider.getCredentials(new AuthScope(server, null, null), context);
            if (credentials != null) {
                BasicScheme basicScheme = new BasicScheme();
                basicScheme.initPreemptive(credentials);
                authCache.put(server, basicScheme);
            }
            if (proxy != null) {
                Credentials proxyCreds = credentialsProvider.getCredentials(new AuthScope(proxy, null, null), context);
                if (proxyCreds != null) {
                    BasicScheme proxyScheme = new BasicScheme();
                    proxyScheme.initPreemptive(proxyCreds);
                    authCache.put(proxy, proxyScheme);
                }
            }
        }
        if (supportWebDav) {
            if (state.getWebDav() == null && (put || isPayloadPresent(request))) {
                HttpOptions req = commonHeaders(new HttpOptions(request.getUri()));
                try (CloseableHttpResponse response = client.execute(server, req, context)) {
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
            try (CloseableHttpResponse response =
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
            try (CloseableHttpResponse response =
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

    private <T extends ClassicHttpRequest> T entity(T request, HttpEntity entity) {
        request.setEntity(entity);
        return request;
    }

    private boolean isPayloadPresent(ClassicHttpRequest request) {
        HttpEntity entity = request.getEntity();
        return entity != null && entity.getContentLength() != 0;
    }

    private <T extends ClassicHttpRequest> T commonHeaders(T request) {
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

    private <T extends ClassicHttpRequest> void resume(T request, GetTask task) throws IOException {
        long resumeOffset = task.getResumeOffset();
        if (resumeOffset > 0L && task.getDataPath() != null) {
            long lastModified = Files.getLastModifiedTime(task.getDataPath()).toMillis();
            request.setHeader(HttpHeaders.RANGE, "bytes=" + resumeOffset + '-');
            request.setHeader(
                    HttpHeaders.IF_UNMODIFIED_SINCE,
                    DateUtils.formatStandardDate(java.time.Instant.ofEpochMilli(lastModified - 60L * 1000L)));
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
                entity = new ByteArrayEntity(new byte[0], ContentType.APPLICATION_OCTET_STREAM);
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
                try (PathProcessor.CollocatedTempFile tempFile = pathProcessor.newTempFile(dataFile)) {
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
                    java.time.Instant lastModified = DateUtils.parseStandardDate(lastModifiedHeader.getValue());
                    if (lastModified != null) {
                        pathProcessor.setLastModified(
                                task.getDataPath(),
                                HttpTransporterUtils.clampRemoteLastModified(lastModified.toEpochMilli()));
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

    private static Map<TransferEvent.TransportPropertyKey, Object> createTransportProperties(
            ClassicHttpResponse response, HttpCoreContext context) {
        HttpTransportPropertiesBuilder builder =
                new HttpTransportPropertiesBuilder(toHttpVersion(response.getVersion()));
        HttpClientContext clientContext = HttpClientContext.cast(context);
        SSLSession sslSession = clientContext.getSSLSession();
        if (sslSession != null) {
            builder.withSslProtocol(sslSession.getProtocol());
            builder.withSslCipherSuite(sslSession.getCipherSuite());
        }
        return builder.build();
    }

    static HttpVersion toHttpVersion(ProtocolVersion version) {
        if (version == null) {
            return HttpVersion.HTTP_1_1;
        }
        switch (version.getMajor()) {
            case 1:
                if (version.getMinor() == 0) {
                    return HttpVersion.HTTP_1_0;
                } else {
                    return HttpVersion.HTTP_1_1;
                }
            case 2:
                return HttpVersion.HTTP_2;
            case 3:
                return HttpVersion.HTTP_3;
            default:
                throw new IllegalArgumentException("Unknown version " + version.toString());
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
            super((ContentType) null, (String) null, false);
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
            if (isConnectionTerminationDrain()) {
                // DefaultBHttpClientConnection#terminateRequest(ClassicHttpRequest) (org.apache.hc.core5, verified
                // against 5.3.3 bytecode) calls HttpEntity#writeTo(...) directly - bypassing sendRequestEntity(...)
                // entirely - to drain a small (0 < Content-Length <= 1024) body that was never sent because a final
                // response (e.g. an auth challenge) arrived before the entity was written, so the connection can
                // still be pooled for reuse; skipping this write, as previously done, leaves a re-pooled connection
                // with the server still awaiting body bytes. This is HTTP wire-protocol framing housekeeping, not a
                // real upload attempt though, so write the bytes directly instead of through utilPut(): going
                // through utilPut() here would fire TransportListener#transportStarted()/transportProgressed() for
                // requests that never actually attempted to upload anything.
                try (InputStream is = task.newInputStream()) {
                    byte[] buffer = new byte[1024];
                    int n;
                    while ((n = is.read(buffer)) >= 0) {
                        os.write(buffer, 0, n);
                    }
                }
                return;
            }
            try {
                utilPut(task, os, false);
            } catch (TransferCancelledException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }

        private boolean isConnectionTerminationDrain() {
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                if ("terminateRequest".equals(ste.getMethodName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void close() throws IOException {
            // no-op
        }
    }

    /**
     * Retry strategy restoring HC4-compatible retry semantics on top of HC5's {@link DefaultHttpRequestRetryStrategy}:
     * I/O-exception retries fire immediately (HC4 did not sleep between them, unlike HC5's default), a response
     * carrying a Retry-After that would exceed {@code retryIntervalMax} fails the request instead of being retried
     * after a truncated sleep, and both the historical "standard" and "default" {@code retryHandler.name} modes are
     * preserved.
     */
    private static class ResolverHttpRequestRetryStrategy extends DefaultHttpRequestRetryStrategy {

        private static final Set<Class<? extends IOException>> NON_RETRIABLE_IO_EXCEPTIONS =
                new java.util.HashSet<>(java.util.Arrays.asList(
                        InterruptedIOException.class,
                        javax.net.ssl.SSLException.class,
                        java.net.UnknownHostException.class,
                        java.net.ConnectException.class));

        private final int retryCount;

        private final long retryInterval;

        private final long retryIntervalMax;

        private final boolean requestSentRetryEnabled;

        private final boolean standard;

        private ResolverHttpRequestRetryStrategy(
                int retryCount,
                long retryInterval,
                long retryIntervalMax,
                Set<Integer> serviceUnavailableHttpCodes,
                String retryHandlerName,
                boolean requestSentRetryEnabled) {
            super(
                    validateNotNegative(retryCount, "retryCount"),
                    TimeValue.ofMilliseconds(validateNotNegative(retryInterval, "retryInterval")),
                    NON_RETRIABLE_IO_EXCEPTIONS,
                    requireNonNull(serviceUnavailableHttpCodes, "serviceUnavailableHttpCodes"));
            if (retryIntervalMax < 0L) {
                throw new IllegalArgumentException("retryIntervalMax must be >= 0");
            }
            this.retryCount = retryCount;
            this.retryInterval = retryInterval;
            this.retryIntervalMax = retryIntervalMax;
            this.requestSentRetryEnabled = requestSentRetryEnabled;
            if (HTTP_RETRY_HANDLER_NAME_STANDARD.equals(retryHandlerName)) {
                this.standard = true;
            } else if (HTTP_RETRY_HANDLER_NAME_DEFAULT.equals(retryHandlerName)) {
                this.standard = false;
            } else {
                throw new IllegalArgumentException(
                        "Unsupported parameter " + CONFIG_PROP_HTTP_RETRY_HANDLER_NAME + " value: " + retryHandlerName);
            }
        }

        private static int validateNotNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be >= 0");
            }
            return value;
        }

        private static long validateNotNegative(long value, String name) {
            if (value < 0L) {
                throw new IllegalArgumentException(name + " must be >= 0");
            }
            return value;
        }

        @Override
        public boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context) {
            if (execCount > retryCount) {
                return false;
            }
            for (Class<? extends IOException> nonRetriable : NON_RETRIABLE_IO_EXCEPTIONS) {
                if (nonRetriable.isInstance(exception)) {
                    return false;
                }
            }
            if (standard) {
                String method = request.getMethod();
                if ("GET".equalsIgnoreCase(method)
                        || "HEAD".equalsIgnoreCase(method)
                        || "PUT".equalsIgnoreCase(method)
                        || "OPTIONS".equalsIgnoreCase(method)) {
                    return true;
                }
                return requestSentRetryEnabled;
            }
            // HC4's "default" handler retried any request that had not yet been fully sent, i.e.
            // (!requestSent || requestSentRetryEnabled). HC5's HttpRequestRetryStrategy exposes no signal for
            // whether the request line/body was actually written to the wire, so !requestSent cannot be
            // reproduced here. Widen coverage as far as the HC5 API permits by additionally retrying idempotent
            // methods (DefaultHttpRequestRetryStrategy#handleAsIdempotent), instead of narrowing to bare
            // requestSentRetryEnabled as before.
            return requestSentRetryEnabled || handleAsIdempotent(request);
        }

        @Override
        public TimeValue getRetryInterval(
                HttpRequest request, IOException exception, int execCount, HttpContext context) {
            // HC4 retried I/O errors immediately; reproduce that instead of HC5's default inter-attempt sleep.
            return TimeValue.ZERO_MILLISECONDS;
        }

        @Override
        public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
            if (!super.retryRequest(response, execCount, context)) {
                return false;
            }
            // Fail fast instead of retrying when the computed interval (including a server Retry-After) would
            // exceed retryIntervalMax, per the documented contract of
            // aether.connector.http.retryHandler.intervalMax.
            return getRetryInterval(response, execCount, context).toMilliseconds() <= retryIntervalMax;
        }

        @Override
        public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
            // Deliberately not delegated to DefaultHttpRequestRetryStrategy#getRetryInterval(HttpResponse,...):
            // that base implementation falls back to a constant defaultRetryInterval when no Retry-After header is
            // present, whereas this transport's documented/tested contract is a linear back-off
            // (execCount * retryInterval) for that case. Retry-After parsing below mirrors the base class but uses
            // the non-deprecated DateUtils.parseStandardDate. The result is intentionally left uncapped here;
            // retryRequest(HttpResponse,...) above is what compares it against retryIntervalMax and fails fast.
            Header header = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
            if (header != null && header.getValue() != null) {
                String headerValue = header.getValue();
                if (headerValue.contains(":")) {
                    java.time.Instant when = DateUtils.parseStandardDate(headerValue);
                    if (when != null) {
                        long diff = Math.max(when.toEpochMilli() - System.currentTimeMillis(), 0L);
                        return TimeValue.ofMilliseconds(diff);
                    }
                } else {
                    try {
                        long seconds = Long.parseLong(headerValue);
                        return TimeValue.ofMilliseconds(seconds * 1000L);
                    } catch (NumberFormatException e) {
                        // fall through to the linear back-off below
                    }
                }
            }
            return TimeValue.ofMilliseconds((long) execCount * retryInterval);
        }
    }
}
