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
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.regex.Matcher;

import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.RetryInterceptor;
import com.github.mizosoft.methanol.RetryInterceptor.Context;
import org.eclipse.aether.ConfigurationProperties;
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
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
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
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.DEFAULT_HTTP_VERSION;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.DEFAULT_MAX_CONCURRENT_REQUESTS;

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

    private final int connectTimeout;

    private final int requestTimeout;

    private final Boolean expectContinue;

    private final Semaphore maxConcurrentRequests;

    private final boolean preemptivePutAuth;

    private final boolean preemptiveAuth;

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
                    if (response.statusCode() >= MULTIPLE_CHOICES) {
                        if (resume && response.statusCode() == PRECONDITION_FAILED) {
                            closeBody(response);
                            resume = false;
                            continue;
                        }
                        try {
                            JdkRFC9457Reporter.INSTANCE.generateException(response, (statusCode, reasonPhrase) -> {
                                throw new HttpTransporterException(statusCode);
                            });
                        } finally {
                            closeBody(response);
                        }
                    }
                } catch (ConnectException e) {
                    closeBody(response);
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

        if (task.getDataLength() == 0L) {
            request.PUT(HttpRequest.BodyPublishers.noBody());
        } else {
            request.PUT(HttpRequest.BodyPublishers.fromPublisher(
                    HttpRequest.BodyPublishers.ofInputStream(() -> {
                        try {
                            return new TransportListenerNotifyingInputStream(
                                    task.newInputStream(), task.getListener(), task.getDataLength());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }),
                    task.getDataLength()));
        }
        prepare(request);
        try {
            HttpResponse<Void> response = send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= MULTIPLE_CHOICES) {
                throw new HttpTransporterException(response.statusCode());
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
            return client.send(request, responseBodyHandler);
        } finally {
            maxConcurrentRequests.release();
        }
    }

    @Override
    protected void implClose() {
        if (client != null) {
            JdkTransporterCloser.closer(client).run();
        }
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

        if (sslContext == null) {
            try {
                if (insecure) {
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
        }

        Methanol.Builder builder = Methanol.newBuilder()
                .version(HttpClient.Version.valueOf(ConfigUtils.getString(
                        session,
                        DEFAULT_HTTP_VERSION,
                        CONFIG_PROP_HTTP_VERSION + "." + repository.getId(),
                        CONFIG_PROP_HTTP_VERSION)))
                .followRedirects(HttpClient.Redirect.NORMAL)
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

        if (repository.getProxy() != null) {
            InetSocketAddress proxyAddress = new InetSocketAddress(
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
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return authentications.get(getRequestorType());
                }
            });
        }

        configureRetryHandler(session, repository, builder);

        return builder.build();
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
                    .backoff(RetryInterceptor.BackoffStrategy.linear(
                            Duration.ofMillis(retryInterval), Duration.ofMillis(retryIntervalMax)))
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
