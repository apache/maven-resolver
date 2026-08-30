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
package org.eclipse.aether.transport.jdk;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.regex.Matcher;

import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.RetryInterceptor;
import com.github.mizosoft.methanol.RetryInterceptor.Context;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.ConfigurationProperties.HttpVersion;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListenerNotifyingInputStream;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor;
import org.eclipse.aether.spi.connector.transport.http.HttpTransportPropertiesBuilder;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.HttpTransportProperty;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.connector.transport.http.HttpTransporterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.ACCEPT_ENCODING;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CACHE_CONTROL;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_LENGTH;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.CONTENT_RANGE_PATTERN;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.IF_UNMODIFIED_SINCE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.LAST_MODIFIED;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.MULTIPLE_CHOICES;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.PRECONDITION_FAILED;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.RANGE;
import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.USER_AGENT;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.CONFIG_PROP_HTTP_VERSION;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.CONFIG_PROP_MAX_CONCURRENT_REQUESTS;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.CONFIG_PROP_UNSCOPED_AUTHENTICATION;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.DEFAULT_MAX_CONCURRENT_REQUESTS;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.DEFAULT_UNSCOPED_AUTHENTICATION;

/**
 * JDK Transport using {@link HttpClient}.
 * <p>
 * Known issues:
 * <ul>
 *     <li>Does not properly support {@link ConfigurationProperties#REQUEST_TIMEOUT} prior Java 26, see <a href="https://bugs.openjdk.org/browse/JDK-8208693">JDK-8208693</a></li>
 * </ul>
 * <p>
 * Related: <a href="https://dev.to/kdrakon/httpclient-can-t-connect-to-a-tls-proxy-118a">No TLS proxy supported</a>.
 *
 * @since 2.0.0
 */
final class JdkTransporter extends AbstractTransporter implements HttpTransporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkTransporter.class);

    private static final DateTimeFormatter RFC7231 = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));

    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    /**
     * Maximum redirect hops followed when this transporter follows redirects itself (origin-scoped headers mode);
     * same limit as the JDK {@link HttpClient}'s own default ({@code jdk.httpclient.redirects.retrylimit}).
     */
    private static final int MAX_REDIRECTS = 5;

    private static final String LOCATION = "Location";

    /**
     * Classes of IOExceptions that should not be retried (because they are permanent failures).
     * Same as in <a href="https://github.com/apache/httpcomponents-client/blob/54900db4653d7f207477e6ee40135b88e9bcf832/httpclient/src/main/java/org/apache/http/impl/client/DefaultHttpRequestRetryHandler.java#L102">
     * Apache HttpClient's DefaultHttpRequestRetryHandler</a>.
     */
    private static final Set<Class<? extends IOException>> NON_RETRIABLE_IO_EXCEPTIONS = Set.of(
            InterruptedIOException.class,
            UnknownHostException.class,
            ConnectException.class,
            NoRouteToHostException.class,
            SSLException.class);

    private final ChecksumExtractor checksumExtractor;

    private final PathProcessor pathProcessor;

    private final URI baseUri;

    private final HttpClient client;

    private final Map<String, String> headers;

    private final boolean originScopedHeaders;

    private final Set<String> crossOriginExcludedHeaders;

    private final int connectTimeout;

    private final int requestTimeout;

    private final Boolean expectContinue;

    private final Semaphore maxConcurrentRequests;

    private final boolean preemptivePutAuth;

    private final boolean preemptiveAuth;

    private final boolean sendRfc9457Accept;

    private PasswordAuthentication serverAuthentication;

    private PasswordAuthentication proxyAuthentication;

    JdkTransporter(
            RepositorySystemSession session,
            RemoteRepository repository,
            int javaVersion,
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

        HashMap<String, String> headers = new HashMap<>();
        String userAgent = HttpTransporterUtils.getUserAgent(session, repository);
        if (userAgent != null) {
            headers.put(USER_AGENT, userAgent);
        }
        Map<String, String> configuredHeaders = HttpTransporterUtils.getHttpHeaders(session, repository);
        if (configuredHeaders != null) {
            headers.putAll(configuredHeaders);
        }
        headers.put(CACHE_CONTROL, "no-cache, no-store");

        this.connectTimeout = HttpTransporterUtils.getHttpConnectTimeout(session, repository);
        this.requestTimeout = HttpTransporterUtils.getHttpRequestTimeout(session, repository);
        Optional<Boolean> expectContinue = HttpTransporterUtils.getHttpExpectContinue(session, repository);
        if (javaVersion > 19) {
            this.expectContinue = expectContinue.orElse(null);
        } else {
            this.expectContinue = null;
            if (expectContinue.isPresent()) {
                LOGGER.warn(
                        "Configuration for Expect-Continue set but is ignored on Java versions below 20 (current java version is {}) due https://bugs.openjdk.org/browse/JDK-8286171",
                        javaVersion);
            }
        }
        final String httpsSecurityMode = HttpTransporterUtils.getHttpsSecurityMode(session, repository);
        final boolean insecure = ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode);

        this.maxConcurrentRequests = new Semaphore(ConfigUtils.getInteger(
                session,
                DEFAULT_MAX_CONCURRENT_REQUESTS,
                CONFIG_PROP_MAX_CONCURRENT_REQUESTS + "." + repository.getId(),
                CONFIG_PROP_MAX_CONCURRENT_REQUESTS));

        this.preemptiveAuth = HttpTransporterUtils.isHttpPreemptiveAuth(session, repository);
        this.preemptivePutAuth = HttpTransporterUtils.isHttpPreemptivePutAuth(session, repository);
        this.sendRfc9457Accept = HttpTransporterUtils.isHttpSendRfc9457Accept(session, repository);

        this.originScopedHeaders = ConfigUtils.getBoolean(
                session,
                JdkTransporterConfigurationKeys.DEFAULT_ORIGIN_SCOPED_HEADERS,
                JdkTransporterConfigurationKeys.CONFIG_PROP_ORIGIN_SCOPED_HEADERS + "." + repository.getId(),
                JdkTransporterConfigurationKeys.CONFIG_PROP_ORIGIN_SCOPED_HEADERS);
        TreeSet<String> crossOriginExcludedHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        // preemptively applied Authorization (see #prepare) must never leave the repository origin either
        crossOriginExcludedHeaders.add("Authorization");
        if (configuredHeaders != null) {
            crossOriginExcludedHeaders.addAll(configuredHeaders.keySet());
        }
        this.crossOriginExcludedHeaders = crossOriginExcludedHeaders;

        this.headers = headers;
        this.client = createClient(session, repository, insecure);
    }

    private URI resolve(TransportTask task) {
        return baseUri.resolve(task.getLocation());
    }

    private ConnectException enhance(ConnectException connectException) {
        ConnectException result = new ConnectException("Connection to " + baseUri.toASCIIString() + " refused");
        result.initCause(connectException);
        return result;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder().uri(resolve(task)).method("HEAD", HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::setHeader);

        prepare(request);
        try {
            HttpResponse<Void> response = send(request.build(), HttpResponse.BodyHandlers.discarding());
            task.getListener().transportPropertiesAvailable(createTransportProperties(response));
            if (response.statusCode() >= MULTIPLE_CHOICES) {
                throw new HttpTransporterException(response.statusCode());
            }
        } catch (ConnectException e) {
            throw enhance(e);
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = task.getResumeOffset() > 0L && task.getDataPath() != null;
        HttpResponse<InputStream> response = null;

        try {
            while (true) {
                HttpRequest.Builder request =
                        HttpRequest.newBuilder().uri(resolve(task)).GET();
                headers.forEach(request::setHeader);
                if (sendRfc9457Accept) {
                    JdkRFC9457Reporter.INSTANCE.prepareRequest(request);
                }

                if (resume) {
                    long resumeOffset = task.getResumeOffset();
                    long lastModified = pathProcessor.lastModified(task.getDataPath(), 0L);
                    request.header(RANGE, "bytes=" + resumeOffset + '-');
                    request.header(
                            IF_UNMODIFIED_SINCE,
                            RFC7231.format(Instant.ofEpochMilli(lastModified - MODIFICATION_THRESHOLD)));
                    request.header(ACCEPT_ENCODING, "identity");
                }

                prepare(request);
                try {
                    response = send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                    task.getListener().transportPropertiesAvailable(createTransportProperties(response));
                    if (response.statusCode() >= MULTIPLE_CHOICES) {
                        if (resume && response.statusCode() == PRECONDITION_FAILED) {
                            closeBody(response);
                            resume = false;
                            continue;
                        }
                        JdkRFC9457Reporter.INSTANCE.generateException(response, (statusCode, reasonPhrase) -> {
                            throw new HttpTransporterException(statusCode);
                        });
                    }
                } catch (ConnectException e) {
                    throw enhance(e);
                }
                break;
            }

            long offset = 0L,
                    length = response.headers().firstValueAsLong(CONTENT_LENGTH).orElse(-1L);
            if (resume) {
                String range = response.headers().firstValue(CONTENT_RANGE).orElse(null);
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
                try (InputStream is = response.body()) {
                    utilGet(task, is, true, length, downloadResumed);
                }
            } else {
                try (PathProcessor.CollocatedTempFile tempFile = pathProcessor.newTempFile(dataFile)) {
                    task.setDataPath(tempFile.getPath(), downloadResumed);
                    if (downloadResumed && Files.isRegularFile(dataFile)) {
                        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(dataFile))) {
                            Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    try (InputStream is = response.body()) {
                        utilGet(task, is, true, length, downloadResumed);
                    }
                    tempFile.move();
                } finally {
                    task.setDataPath(dataFile);
                }
            }
            if (task.getDataPath() != null) {
                String lastModifiedHeader = response.headers()
                        .firstValue(LAST_MODIFIED)
                        .orElse(null); // note: Wagon also does first not last
                if (lastModifiedHeader != null) {
                    try {
                        pathProcessor.setLastModified(
                                task.getDataPath(),
                                ZonedDateTime.parse(lastModifiedHeader, RFC7231)
                                        .toInstant()
                                        .toEpochMilli());
                    } catch (DateTimeParseException e) {
                        // fall through
                    }
                }
            }
            Map<String, String> checksums = checksumExtractor.extractChecksums(headerGetter(response));
            if (checksums != null && !checksums.isEmpty()) {
                checksums.forEach(task::setChecksum);
            }
        } finally {
            closeBody(response);
        }
    }

    private Map<TransferEvent.TransportPropertyKey, Object> createTransportProperties(HttpResponse<?> response) {
        HttpTransportPropertiesBuilder builder = new HttpTransportPropertiesBuilder(toHttpVersion(response.version()));
        response.sslSession().ifPresent(ssl -> {
            builder.withSslProtocol(ssl.getProtocol());
            builder.withSslCipherSuite(ssl.getCipherSuite());
        });
        // TODO: add compression algorithm if any (https://github.com/mizosoft/methanol/issues/182)
        return builder.build();
    }

    static HttpTransportProperty.HttpVersion toHttpVersion(HttpClient.Version version) {
        switch (version) {
            case HTTP_1_1:
                return HttpTransportProperty.HttpVersion.HTTP_1_1;
            case HTTP_2:
                return HttpTransportProperty.HttpVersion.HTTP_2;
            default:
                // support HTTP_3 via name (as only part of Java 26+ API)
                if ("HTTP_3".equals(version.name())) {
                    return HttpTransportProperty.HttpVersion.HTTP_3;
                } else {
                    throw new IllegalArgumentException("Unsupported HTTP version: " + version);
                }
        }
    }

    private static Function<String, String> headerGetter(HttpResponse<?> response) {
        return s -> response.headers().firstValue(s).orElse(null);
    }

    private void closeBody(HttpResponse<InputStream> streamHttpResponse) throws IOException {
        if (streamHttpResponse != null) {
            InputStream body = streamHttpResponse.body();
            if (body != null) {
                body.close();
            }
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(resolve(task));
        if (expectContinue != null) {
            request = request.expectContinue(expectContinue);
        }
        headers.forEach(request::setHeader);
        if (sendRfc9457Accept) {
            JdkRFC9457Reporter.INSTANCE.prepareRequest(request);
        }
        if (task.getDataLength() == 0L) {
            request.PUT(HttpRequest.BodyPublishers.noBody());
        } else {
            request.PUT(HttpRequest.BodyPublishers.fromPublisher(
                    HttpRequest.BodyPublishers.ofInputStream(() -> {
                        try {
                            // transport properties are not available for outgoing requests
                            return new TransportListenerNotifyingInputStream(
                                    task.newInputStream(), task.getListener(), task.getDataLength());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }),
                    // this adds a content-length request header
                    task.getDataLength()));
        }
        prepare(request);
        HttpResponse<InputStream> response = null;
        try {
            response = send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            task.getListener().transportPropertiesAvailable(createTransportProperties(response));
            if (response.statusCode() >= MULTIPLE_CHOICES) {
                JdkRFC9457Reporter.INSTANCE.generateException(response, (statusCode, reasonPhrase) -> {
                    throw new HttpTransporterException(statusCode);
                });
            }
        } catch (ConnectException e) {
            throw enhance(e);
        } catch (IOException e) {
            // unwrap possible underlying exception from body supplier
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof TransferCancelledException) {
                throw (TransferCancelledException) rootCause;
            }
            throw e;
        } finally {
            closeBody(response);
        }
    }

    private void prepare(HttpRequest.Builder requestBuilder) {
        if (preemptiveAuth
                || (preemptivePutAuth && requestBuilder.build().method().equals("PUT"))) {
            if (serverAuthentication != null) {
                // https://stackoverflow.com/a/58612586
                requestBuilder.setHeader(
                        "Authorization",
                        getBasicAuthValue(serverAuthentication.getUserName(), serverAuthentication.getPassword()));
            }
            if (proxyAuthentication != null) {
                requestBuilder.setHeader(
                        "Proxy-Authorization",
                        getBasicAuthValue(proxyAuthentication.getUserName(), proxyAuthentication.getPassword()));
            }
        }
    }

    static String getBasicAuthValue(String username, char[] password) {
        // Java's HTTP client uses ISO-8859-1 for Basic auth encoding
        return "Basic "
                + Base64.getEncoder().encodeToString((username + ':' + String.valueOf(password)).getBytes(ISO_8859_1));
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws Exception {
        maxConcurrentRequests.acquire();
        try {
            if (!originScopedHeaders) {
                return client.send(request, responseBodyHandler);
            }
            // the client is configured with Redirect.NEVER: follow redirects here instead, so that configured
            // headers (and preemptively applied Authorization) can be scoped to the repository origin per hop -
            // the JDK client itself re-sends all user-set headers on every hop it follows, with no per-hop hook
            HttpRequest current = request;
            int redirects = 0;
            while (true) {
                HttpResponse<T> response =
                        client.send(current, discardingOnFollowedRedirect(responseBodyHandler, current.uri()));
                URI target = followableRedirect(
                        response.statusCode(),
                        current.uri(),
                        response.headers().firstValue(LOCATION).orElse(null));
                if (target == null) {
                    return response;
                }
                redirects++;
                if (redirects > MAX_REDIRECTS) {
                    throw new IOException("Too many redirects for " + request.uri() + " (max " + MAX_REDIRECTS
                            + "), last redirect target: " + target);
                }
                current = redirectRequest(current, response.statusCode(), target, baseUri, crossOriginExcludedHeaders);
            }
        } finally {
            maxConcurrentRequests.release();
        }
    }

    /**
     * Body handler that discards the body of responses this transporter is about to follow as redirect (the
     * caller only ever sees the final response of the hop chain), delegating everything else to the original
     * handler.
     */
    private static <T> HttpResponse.BodyHandler<T> discardingOnFollowedRedirect(
            HttpResponse.BodyHandler<T> delegate, URI requestUri) {
        return responseInfo -> {
            String location = responseInfo.headers().firstValue(LOCATION).orElse(null);
            if (followableRedirect(responseInfo.statusCode(), requestUri, location) != null) {
                return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.discarding(), v -> null);
            }
            return delegate.apply(responseInfo);
        };
    }

    /**
     * Returns the resolved redirect target if the response is a redirect this transporter follows, otherwise
     * {@code null}. Follow rules mirror {@link HttpClient.Redirect#NORMAL}: redirect status with a resolvable
     * {@code Location}, {@code http}/{@code https} targets only, and never a downgrade from https to http.
     */
    static URI followableRedirect(int statusCode, URI requestUri, String locationHeader) {
        if (!isRedirect(statusCode) || locationHeader == null) {
            return null;
        }
        final URI target;
        try {
            target = requestUri.resolve(locationHeader);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = target.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        if ("https".equalsIgnoreCase(requestUri.getScheme()) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        return target;
    }

    static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    /**
     * Builds the next-hop request: the previous request re-targeted at the redirect target, with the origin-scoped
     * headers (operator-configured headers and preemptively applied {@code Authorization}) removed when the target
     * is not the repository origin (same scheme, case-insensitive host and effective port). Method rewriting
     * mirrors {@link HttpClient.Redirect#NORMAL}.
     */
    static HttpRequest redirectRequest(
            HttpRequest previous, int statusCode, URI target, URI baseUri, Set<String> crossOriginExcludedHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(target).expectContinue(previous.expectContinue());
        previous.version().ifPresent(builder::version);
        previous.timeout().ifPresent(builder::timeout);
        boolean sameOrigin = isSameOrigin(baseUri, target);
        previous.headers().map().forEach((name, values) -> {
            if (sameOrigin || !crossOriginExcludedHeaders.contains(name)) {
                for (String value : values) {
                    builder.header(name, value);
                }
            }
        });
        String method = previous.method();
        if (statusCode == 303 && !"HEAD".equals(method)) {
            builder.method("GET", HttpRequest.BodyPublishers.noBody());
        } else if ((statusCode == 301 || statusCode == 302) && "POST".equals(method)) {
            builder.method("GET", HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, previous.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        }
        return builder.build();
    }

    static boolean isSameOrigin(URI origin, URI target) {
        if (origin.getScheme() == null
                || origin.getHost() == null
                || target.getScheme() == null
                || target.getHost() == null) {
            return false;
        }
        return origin.getScheme().equalsIgnoreCase(target.getScheme())
                && origin.getHost().equalsIgnoreCase(target.getHost())
                && effectivePort(origin.getScheme(), origin.getPort())
                        == effectivePort(target.getScheme(), target.getPort());
    }

    static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    @Override
    protected void implClose() {
        if (client != null) {
            JdkTransporterCloser.closer(client).run();
        }
    }

    HttpClient.Version getHttpVersion(RepositorySystemSession session, RemoteRepository repository) {
        HttpVersion httpVersion = HttpTransporterUtils.getHttpVersion(session, repository);
        if (httpVersion == ConfigurationProperties.DEFAULT_HTTP_VERSION) {
            // Fall back to legacy JDK Transporter specific property when it is explicitly configured.
            String configuredLegacyHttpVersion = ConfigUtils.getString(
                    session, null, CONFIG_PROP_HTTP_VERSION + "." + repository.getId(), CONFIG_PROP_HTTP_VERSION);
            if (configuredLegacyHttpVersion != null) {
                return resolveHttpVersion(configuredLegacyHttpVersion);
            }
            return HttpClient.Version.HTTP_2;
        } else {
            switch (httpVersion) {
                case MAXIMUM:
                    return getMaximumSupportedHttpVersion();
                case HTTP_1_1:
                    return HttpClient.Version.HTTP_1_1;
                case HTTP_2:
                case DEFAULT:
                    return HttpClient.Version.HTTP_2;
                case HTTP_3:
                    return resolveHttpVersion("HTTP_3");
                default:
                    // unreachable but necessary for Checkstyle to not complain about missing default case
                    throw new IllegalStateException("Unknown HTTP version: " + httpVersion);
            }
        }
    }

    private HttpClient.Version resolveHttpVersion(String requestedVersion) {
        try {
            return HttpClient.Version.valueOf(requestedVersion);
        } catch (IllegalArgumentException e) {
            HttpClient.Version maximumHttpVersion = getMaximumSupportedHttpVersion();
            LOGGER.warn(
                    "HTTP version '{}' is not supported by the running JRE, using '{}' instead",
                    requestedVersion,
                    maximumHttpVersion);
            return maximumHttpVersion;
        }
    }

    HttpClient.Version getMaximumSupportedHttpVersion() {
        HttpClient.Version[] values = HttpClient.Version.values();
        return values[values.length - 1];
    }

    private HttpClient createClient(RepositorySystemSession session, RemoteRepository repository, boolean insecure)
            throws RuntimeException {

        HashMap<Authenticator.RequestorType, PasswordAuthentication> authentications = new HashMap<>();
        SSLContext sslContext = null;
        try (AuthenticationContext repoAuthContext = AuthenticationContext.forRepository(session, repository)) {
            if (repoAuthContext != null) {
                sslContext = repoAuthContext.get(AuthenticationContext.SSL_CONTEXT, SSLContext.class);

                String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                String password = repoAuthContext.get(AuthenticationContext.PASSWORD);
                serverAuthentication = new PasswordAuthentication(username, password.toCharArray());
                authentications.put(Authenticator.RequestorType.SERVER, serverAuthentication);
            }
        }

        Version httpVersion = getHttpVersion(session, repository);
        if (sslContext == null) {
            try {
                if (insecure) {
                    if (httpVersion.name().equals("HTTP_3")) {
                        // custom trust manager not supported for HTTP/3 (Quic)
                        // (https://github.com/openjdk/jdk/blob/631b675d7949a0e6312d8d6f45e2515d53b12f05/src/java.base/share/classes/sun/security/ssl/SSLContextImpl.java#L529)
                        // https://openjdk.org/jeps/517
                        // https://mail.openjdk.org/archives/list/net-dev@openjdk.org/thread/LHSC7MWRFDJE2KGS2QMPFJPZX3XEQKOQ/
                        throw new IllegalStateException(
                                "Insecure HTTPS connections are not supported for HTTP/3 (Quic)");
                    }
                    sslContext = SSLContext.getInstance("TLS");
                    X509ExtendedTrustManager tm = new X509ExtendedTrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
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
        } else {
            if (insecure) {
                throw new IllegalStateException(
                        "Insecure HTTPS connections are not supported when a custom SSLContext is configured");
            }
        }

        Methanol.Builder builder = Methanol.newBuilder()
                .version(httpVersion)
                // with origin-scoped headers (default) the transporter follows redirects itself (see #send) so
                // that configured headers can be dropped on hops leaving the repository origin; the JDK client
                // has no per-hop hook and re-sends all user-set headers on every hop it follows
                .followRedirects(originScopedHeaders ? HttpClient.Redirect.NEVER : HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(connectTimeout))
                // this only considers the time until the response header is received, see
                // https://bugs.openjdk.org/browse/JDK-8208693
                // but better than nothing
                .requestTimeout(Duration.ofMillis(requestTimeout))
                .sslContext(sslContext);

        if (insecure) {
            SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);
            builder.sslParameters(sslParameters);
        }

        setLocalAddress(
                builder,
                HttpTransporterUtils.getHttpLocalAddress(session, repository).orElse(null));

        InetSocketAddress proxyAddress = null;
        if (repository.getProxy() != null) {
            proxyAddress = new InetSocketAddress(
                    repository.getProxy().getHost(), repository.getProxy().getPort());
            if (proxyAddress.isUnresolved()) {
                throw new IllegalStateException(
                        "Proxy host " + repository.getProxy().getHost() + " could not be resolved");
            }
            builder.proxy(ProxySelector.of(proxyAddress));
            try (AuthenticationContext proxyAuthContext = AuthenticationContext.forProxy(session, repository)) {
                if (proxyAuthContext != null) {
                    String username = proxyAuthContext.get(AuthenticationContext.USERNAME);
                    String password = proxyAuthContext.get(AuthenticationContext.PASSWORD);

                    proxyAuthentication = new PasswordAuthentication(username, password.toCharArray());
                    authentications.put(Authenticator.RequestorType.PROXY, proxyAuthentication);
                }
            }
        }

        if (!authentications.isEmpty()) {
            boolean unscopedAuthentication = ConfigUtils.getBoolean(
                    session,
                    DEFAULT_UNSCOPED_AUTHENTICATION,
                    CONFIG_PROP_UNSCOPED_AUTHENTICATION + "." + repository.getId(),
                    CONFIG_PROP_UNSCOPED_AUTHENTICATION);
            if (unscopedAuthentication) {
                // legacy behavior, explicit opt-in only: hands out credentials to ANY challenging host
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return authentications.get(getRequestorType());
                    }
                });
            } else {
                builder.authenticator(new ScopedAuthenticator(baseUri, proxyAddress, authentications));
            }
        }

        configureRetryHandler(session, repository, builder);

        return builder.build();
    }

    /**
     * An {@link Authenticator} that only hands out credentials to the origin it was configured for. The JDK
     * {@link HttpClient} consults the authenticator for any host that answers with an authentication challenge -
     * including hosts reached by following redirects off the repository - so the requesting protocol, host and
     * port must be verified against the repository base URI (or, for proxy challenges, against the configured
     * proxy address) before any credential is returned. Legacy unscoped behavior is available as explicit opt-in
     * via {@link JdkTransporterConfigurationKeys#CONFIG_PROP_UNSCOPED_AUTHENTICATION}.
     */
    static final class ScopedAuthenticator extends Authenticator {
        private final URI baseUri;

        private final InetSocketAddress proxyAddress;

        private final Map<RequestorType, PasswordAuthentication> authentications;

        ScopedAuthenticator(
                URI baseUri,
                InetSocketAddress proxyAddress,
                Map<RequestorType, PasswordAuthentication> authentications) {
            this.baseUri = Objects.requireNonNull(baseUri);
            this.proxyAddress = proxyAddress;
            this.authentications = new HashMap<>(authentications);
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            PasswordAuthentication authentication = authentications.get(getRequestorType());
            if (authentication == null) {
                return null;
            }
            if (getRequestorType() == RequestorType.PROXY) {
                if (proxyAddress == null
                        || getRequestingHost() == null
                        || !proxyAddress.getHostString().equalsIgnoreCase(getRequestingHost())
                        || proxyAddress.getPort() != getRequestingPort()) {
                    LOGGER.warn(
                            "Refusing to send proxy credentials to '{}:{}': not the configured proxy '{}'",
                            getRequestingHost(),
                            getRequestingPort(),
                            proxyAddress);
                    return null;
                }
                return authentication;
            }
            // RequestorType.SERVER: the challenge must originate from the repository itself
            if (!originMatchesBaseUri(getRequestingProtocol(), getRequestingHost(), getRequestingPort())) {
                LOGGER.warn(
                        "Refusing to send repository credentials to '{}://{}:{}': it does not match the repository"
                                + " base URI '{}' (a redirect may have left the repository host); set the"
                                + " configuration property {}=true to restore the legacy unscoped behavior",
                        getRequestingProtocol(),
                        getRequestingHost(),
                        getRequestingPort(),
                        baseUri,
                        CONFIG_PROP_UNSCOPED_AUTHENTICATION);
                return null;
            }
            return authentication;
        }

        private boolean originMatchesBaseUri(String protocol, String host, int port) {
            if (protocol == null || host == null || baseUri.getScheme() == null || baseUri.getHost() == null) {
                return false;
            }
            return protocol.equalsIgnoreCase(baseUri.getScheme())
                    && host.equalsIgnoreCase(baseUri.getHost())
                    && effectivePort(protocol, port) == effectivePort(baseUri.getScheme(), baseUri.getPort());
        }

        private static int effectivePort(String protocol, int port) {
            if (port >= 0) {
                return port;
            }
            return "https".equalsIgnoreCase(protocol) ? 443 : 80;
        }
    }

    private static class RetryLoggingListener implements RetryInterceptor.Listener {
        private final int maxNumRetries;

        RetryLoggingListener(int maxNumRetries) {
            this.maxNumRetries = maxNumRetries;
        }

        @Override
        public void onRetry(Context<?> context, HttpRequest nextRequest, Duration delay) {
            LOGGER.warn(
                    "{} request to {} failed (attempt {} of {}) due to {}. Retrying in {} ms...",
                    context.request().method(),
                    context.request().uri(),
                    context.retryCount() + 1,
                    maxNumRetries + 1,
                    getReason(context),
                    delay.toMillis());
        }

        String getReason(Context<?> context) {
            if (context.exception().isPresent()) {
                return context.exception().get().getMessage();
            } else if (context.response().isPresent()) {
                return "status " + context.response().get().statusCode();
            }
            // should not happen
            throw new IllegalStateException("No exception or response present in retry context");
        }
    }

    private static void configureRetryHandler(
            RepositorySystemSession session, RemoteRepository repository, Methanol.Builder builder) {
        int retryCount = HttpTransporterUtils.getHttpRetryHandlerCount(session, repository);
        long retryInterval = HttpTransporterUtils.getHttpRetryHandlerInterval(session, repository);
        long retryIntervalMax = HttpTransporterUtils.getHttpRetryHandlerIntervalMax(session, repository);
        if (retryCount > 0) {
            Methanol.Interceptor rateLimitingRetryInterceptor = RetryInterceptor.newBuilder()
                    .maxRetries(retryCount)
                    .onStatus(HttpTransporterUtils.getHttpServiceUnavailableCodes(session, repository)::contains)
                    .listener(new RetryLoggingListener(retryCount))
                    .backoff(RetryInterceptor.BackoffStrategy.retryAfterOr(RetryInterceptor.BackoffStrategy.linear(
                            Duration.ofMillis(retryInterval), Duration.ofMillis(retryIntervalMax))))
                    .build();
            builder.interceptor(rateLimitingRetryInterceptor);
            Methanol.Interceptor retryIoExceptionsInterceptor = RetryInterceptor.newBuilder()
                    // this is in addition to the JDK internal retries (https://github.com/mizosoft/methanol/issues/174)
                    // e.g. for connection timeouts this is hardcoded to 2 attempts:
                    // https://github.com/openjdk/jdk/blob/640343f7d94894b0378ea5b1768eeac203a9aaf8/src/java.net.http/share/classes/jdk/internal/net/http/MultiExchange.java#L665
                    .maxRetries(retryCount)
                    .onException(t -> {
                        // exceptions from body publishers are wrapped inside IOExceptions
                        // but hard to distinguish from others, so just exclude some we know are emitted from body
                        // suppliers (https://github.com/mizosoft/methanol/issues/179)
                        Throwable rootCause = getRootCause(t);
                        return t instanceof IOException
                                && !NON_RETRIABLE_IO_EXCEPTIONS.contains(t.getClass())
                                && !(rootCause instanceof TransferCancelledException);
                    })
                    .listener(new RetryLoggingListener(retryCount))
                    .build();
            builder.interceptor(retryIoExceptionsInterceptor);
        }
    }

    private static void setLocalAddress(HttpClient.Builder builder, InetAddress address) {
        if (address == null) {
            return;
        }
        try {
            final Method mtd = builder.getClass().getDeclaredMethod("localAddress", InetAddress.class);
            if (!mtd.canAccess(builder)) {
                mtd.setAccessible(true);
            }
            mtd.invoke(builder, address);
        } catch (final NoSuchMethodException ignore) {
            // skip, not yet in the API
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Throwable getRootCause(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
