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

import javax.net.ssl.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
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
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.aether.spi.connector.transport.http.HttpConstants.*;
import static org.eclipse.aether.transport.jdk.JdkTransporterConfigurationKeys.*;

/**
 * JDK Transport using {@link HttpClient}.
 *
 * @since 2.0.0
 */
@SuppressWarnings({"checkstyle:magicnumber"})
final class JdkTransporter extends AbstractTransporter implements HttpTransporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkTransporter.class);

    private static final DateTimeFormatter RFC7231 = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));

    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    private final ChecksumExtractor checksumExtractor;

    private final PathProcessor pathProcessor;

    private final URI baseUri;

    private final HttpClient client;

    private final Map<String, String> headers;

    private final int requestTimeout;

    private final Boolean expectContinue;

    private final Semaphore maxConcurrentRequests;

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
        String userAgent = ConfigUtils.getString(
                session, ConfigurationProperties.DEFAULT_USER_AGENT, ConfigurationProperties.USER_AGENT);
        if (userAgent != null) {
            headers.put(USER_AGENT, userAgent);
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> configuredHeaders = (Map<Object, Object>) ConfigUtils.getMap(
                session,
                Collections.emptyMap(),
                ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                ConfigurationProperties.HTTP_HEADERS);
        if (configuredHeaders != null) {
            configuredHeaders.forEach((k, v) -> headers.put(String.valueOf(k), v != null ? String.valueOf(v) : null));
        }
        headers.put(CACHE_CONTROL, "no-cache, no-store");

        this.requestTimeout = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT);
        String expectContinueConf = ConfigUtils.getString(
                session,
                null,
                ConfigurationProperties.HTTP_EXPECT_CONTINUE + "." + repository.getId(),
                ConfigurationProperties.HTTP_EXPECT_CONTINUE);
        if (javaVersion > 19) {
            this.expectContinue = expectContinueConf == null ? null : Boolean.parseBoolean(expectContinueConf);
        } else {
            this.expectContinue = null;
            if (expectContinueConf != null) {
                LOGGER.warn(
                        "Configuration for Expect-Continue set but is ignored on Java versions below 20 (current java version is {}) due https://bugs.openjdk.org/browse/JDK-8286171",
                        javaVersion);
            }
        }

        this.maxConcurrentRequests = new Semaphore(ConfigUtils.getInteger(
                session,
                DEFAULT_MAX_CONCURRENT_REQUESTS,
                CONFIG_PROP_MAX_CONCURRENT_REQUESTS + "." + repository.getId(),
                CONFIG_PROP_MAX_CONCURRENT_REQUESTS));

        this.headers = headers;
        this.client = getOrCreateClient(session, repository, javaVersion);
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
    public int classify(Throwable error) {
        if (error instanceof HttpTransporterException
                && ((HttpTransporterException) error).getStatusCode() == NOT_FOUND) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(resolve(task))
                .timeout(Duration.ofMillis(requestTimeout))
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
        headers.forEach(request::setHeader);
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
                HttpRequest.Builder request = HttpRequest.newBuilder()
                        .uri(resolve(task))
                        .timeout(Duration.ofMillis(requestTimeout))
                        .method("GET", HttpRequest.BodyPublishers.noBody());
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

                try {
                    response = send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() >= MULTIPLE_CHOICES) {
                        closeBody(response);
                        if (resume && response.statusCode() == PRECONDITION_FAILED) {
                            resume = false;
                            continue;
                        }
                        throw new HttpTransporterException(response.statusCode());
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
                try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile)) {
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
        HttpRequest.Builder request =
                HttpRequest.newBuilder().uri(resolve(task)).timeout(Duration.ofMillis(requestTimeout));
        if (expectContinue != null) {
            request = request.expectContinue(expectContinue);
        }
        headers.forEach(request::setHeader);
        try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            utilPut(task, Files.newOutputStream(tempFile.getPath()), true);
            request.method("PUT", HttpRequest.BodyPublishers.ofFile(tempFile.getPath()));

            try {
                HttpResponse<Void> response = send(request.build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= MULTIPLE_CHOICES) {
                    throw new HttpTransporterException(response.statusCode());
                }
            } catch (ConnectException e) {
                throw enhance(e);
            }
        }
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        maxConcurrentRequests.acquire();
        try {
            return client.send(request, responseBodyHandler);
        } finally {
            maxConcurrentRequests.release();
        }
    }

    @Override
    protected void implClose() {
        // no-op
    }

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

    /**
     * Visible for testing.
     */
    static final String HTTP_INSTANCE_KEY_PREFIX = JdkTransporterFactory.class.getName() + ".http.";

    private HttpClient getOrCreateClient(RepositorySystemSession session, RemoteRepository repository, int javaVersion)
            throws NoTransporterException {
        final String instanceKey = HTTP_INSTANCE_KEY_PREFIX + repository.getId();

        final String httpsSecurityMode = ConfigUtils.getString(
                session,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                ConfigurationProperties.HTTPS_SECURITY_MODE + "." + repository.getId(),
                ConfigurationProperties.HTTPS_SECURITY_MODE);

        if (!ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT.equals(httpsSecurityMode)
                && !ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode)) {
            throw new IllegalArgumentException("Unsupported '" + httpsSecurityMode + "' HTTPS security mode.");
        }
        final boolean insecure = ConfigurationProperties.HTTPS_SECURITY_MODE_INSECURE.equals(httpsSecurityMode);

        // todo: normally a single client per JVM is sufficient - in particular cause part of the config
        //       is global and not per instance so we should create a client only when conf changes for a repo
        //       else fallback on a global client
        try {
            return (HttpClient) session.getData().computeIfAbsent(instanceKey, () -> {
                HashMap<Authenticator.RequestorType, PasswordAuthentication> authentications = new HashMap<>();
                SSLContext sslContext = null;
                try {
                    try (AuthenticationContext repoAuthContext =
                            AuthenticationContext.forRepository(session, repository)) {
                        if (repoAuthContext != null) {
                            sslContext = repoAuthContext.get(AuthenticationContext.SSL_CONTEXT, SSLContext.class);

                            String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                            String password = repoAuthContext.get(AuthenticationContext.PASSWORD);

                            authentications.put(
                                    Authenticator.RequestorType.SERVER,
                                    new PasswordAuthentication(username, password.toCharArray()));
                        }
                    }

                    if (sslContext == null) {
                        if (insecure) {
                            sslContext = SSLContext.getInstance("TLS");
                            X509ExtendedTrustManager tm = new X509ExtendedTrustManager() {
                                @Override
                                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                                @Override
                                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                                @Override
                                public void checkClientTrusted(
                                        X509Certificate[] chain, String authType, Socket socket) {}

                                @Override
                                public void checkServerTrusted(
                                        X509Certificate[] chain, String authType, Socket socket) {}

                                @Override
                                public void checkClientTrusted(
                                        X509Certificate[] chain, String authType, SSLEngine engine) {}

                                @Override
                                public void checkServerTrusted(
                                        X509Certificate[] chain, String authType, SSLEngine engine) {}

                                @Override
                                public X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }
                            };
                            sslContext.init(null, new X509TrustManager[] {tm}, null);
                        } else {
                            sslContext = SSLContext.getDefault();
                        }
                    }

                    int connectTimeout = ConfigUtils.getInteger(
                            session,
                            ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                            ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                            ConfigurationProperties.CONNECT_TIMEOUT);

                    HttpClient.Builder builder = HttpClient.newBuilder()
                            .version(HttpClient.Version.valueOf(ConfigUtils.getString(
                                    session,
                                    DEFAULT_HTTP_VERSION,
                                    CONFIG_PROP_HTTP_VERSION + "." + repository.getId(),
                                    CONFIG_PROP_HTTP_VERSION)))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofMillis(connectTimeout))
                            .sslContext(sslContext);

                    if (insecure) {
                        SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                        sslParameters.setEndpointIdentificationAlgorithm(null);
                        builder.sslParameters(sslParameters);
                    }

                    setLocalAddress(builder, () -> getHttpLocalAddress(session, repository));

                    if (repository.getProxy() != null) {
                        ProxySelector proxy = ProxySelector.of(new InetSocketAddress(
                                repository.getProxy().getHost(),
                                repository.getProxy().getPort()));

                        builder.proxy(proxy);
                        try (AuthenticationContext proxyAuthContext =
                                AuthenticationContext.forProxy(session, repository)) {
                            if (proxyAuthContext != null) {
                                String username = proxyAuthContext.get(AuthenticationContext.USERNAME);
                                String password = proxyAuthContext.get(AuthenticationContext.PASSWORD);

                                authentications.put(
                                        Authenticator.RequestorType.PROXY,
                                        new PasswordAuthentication(username, password.toCharArray()));
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

                    HttpClient result = builder.build();
                    if (!session.addOnSessionEndedHandler(JdkTransporterCloser.closer(javaVersion, result))) {
                        LOGGER.warn(
                                "Using Resolver 2 feature without Resolver 2 session handling, you may leak resources.");
                    }

                    return result;
                } catch (Exception e) {
                    throw new WrapperEx(e);
                }
            });
        } catch (WrapperEx e) {
            throw new NoTransporterException(repository, e.getCause());
        }
    }

    private void setLocalAddress(HttpClient.Builder builder, Supplier<InetAddress> addressSupplier) {
        try {
            final InetAddress address = addressSupplier.get();
            if (address == null) {
                return;
            }

            final Method mtd = builder.getClass().getDeclaredMethod("localAddress", InetAddress.class);
            if (!mtd.canAccess(builder)) {
                mtd.setAccessible(true);
            }
            mtd.invoke(builder, address);
        } catch (final NoSuchMethodException nsme) {
            // skip, not yet in the API
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class WrapperEx extends RuntimeException {
        private WrapperEx(Throwable cause) {
            super(cause);
        }
    }
}
