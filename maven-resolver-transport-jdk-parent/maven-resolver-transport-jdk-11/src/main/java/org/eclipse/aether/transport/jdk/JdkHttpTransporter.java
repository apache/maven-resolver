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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;

/**
 * JDK Transport using {@link java.net.http.HttpClient}.
 *
 * @since TBD
 */
final class JdkHttpTransporter extends AbstractTransporter {
    private static final int MULTIPLE_CHOICES = 300;

    private static final int NOT_FOUND = 404;

    private static final int PRECONDITION_FAILED = 412;

    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    private static final String CACHE_CONTROL = "Cache-Control";

    private static final String CONTENT_LENGTH = "Content-Length";

    private static final String CONTENT_RANGE = "Content-Range";

    private static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    private static final String RANGE = "Range";

    private static final String USER_AGENT = "User-Agent";

    private static final String LAST_MODIFIED = "Last-Modified";

    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile("\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*");

    private final URI baseUri;

    private final HttpClient client;

    private final Map<String, String> headers;

    private final int requestTimeout;

    JdkHttpTransporter(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException {
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

        this.headers = headers;
        this.client = getOrCreateClient(session, repository);
    }

    private URI resolve(TransportTask task) {
        return baseUri.resolve(task.getLocation());
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof JdkHttpException && ((JdkHttpException) error).getStatusCode() == NOT_FOUND) {
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
        HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= MULTIPLE_CHOICES) {
            throw new JdkHttpException(response.statusCode());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = task.getResumeOffset() > 0L && task.getDataFile() != null;
        HttpResponse<InputStream> response;

        while (true) {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(resolve(task))
                    .timeout(Duration.ofMillis(requestTimeout))
                    .method("GET", HttpRequest.BodyPublishers.noBody());
            headers.forEach(request::setHeader);

            if (resume) {
                long resumeOffset = task.getResumeOffset();
                request.header(RANGE, "bytes=" + resumeOffset + '-');
                request.header(
                        IF_UNMODIFIED_SINCE,
                        RFC7231.format(
                                Instant.ofEpochMilli(task.getDataFile().lastModified() - MODIFICATION_THRESHOLD)));
                request.header(ACCEPT_ENCODING, "identity");
            }

            response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= MULTIPLE_CHOICES) {
                if (resume && response.statusCode() == PRECONDITION_FAILED) {
                    resume = false;
                    continue;
                }
                throw new JdkHttpException(response.statusCode());
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
        final File dataFile = task.getDataFile();
        if (dataFile == null) {
            try (InputStream is = response.body()) {
                utilGet(task, is, true, length, downloadResumed);
            }
        } else {
            try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile.toPath())) {
                task.setDataFile(tempFile.getPath().toFile(), downloadResumed);
                if (downloadResumed && Files.isRegularFile(dataFile.toPath())) {
                    try (InputStream inputStream = Files.newInputStream(dataFile.toPath())) {
                        Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                try (InputStream is = response.body()) {
                    utilGet(task, is, true, length, downloadResumed);
                }
                tempFile.move();
            } finally {
                task.setDataFile(dataFile);
            }
        }
        if (task.getDataFile() != null) {
            String lastModifiedHeader =
                    response.headers().firstValue(LAST_MODIFIED).orElse(null); // note: Wagon also does first not last
            if (lastModifiedHeader != null) {
                try {
                    Files.setLastModifiedTime(
                            task.getDataFile().toPath(),
                            FileTime.fromMillis(ZonedDateTime.parse(lastModifiedHeader, RFC7231)
                                    .toInstant()
                                    .toEpochMilli()));
                } catch (DateTimeParseException e) {
                    // fall through
                }
            }
        }
        Map<String, String> checksums = extractXChecksums(response);
        if (checksums != null) {
            checksums.forEach(task::setChecksum);
            return;
        }
        checksums = extractNexus2Checksums(response);
        if (checksums != null) {
            checksums.forEach(task::setChecksum);
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder().uri(resolve(task)).timeout(Duration.ofMillis(requestTimeout));
        headers.forEach(request::setHeader);
        try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            utilPut(task, Files.newOutputStream(tempFile.getPath()), true);
            request.method("PUT", HttpRequest.BodyPublishers.ofFile(tempFile.getPath()));

            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= MULTIPLE_CHOICES) {
                throw new JdkHttpException(response.statusCode());
            }
        }
    }

    @Override
    protected void implClose() {
        // nop
    }

    private static Map<String, String> extractXChecksums(HttpResponse<?> response) {
        String value;
        HashMap<String, String> result = new HashMap<>();
        // Central style: x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.headers().firstValue("x-checksum-sha1").orElse(null);
        if (value != null) {
            result.put("SHA-1", value);
        }
        // Central style: x-checksum-md5: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.headers().firstValue("x-checksum-md5").orElse(null);
        if (value != null) {
            result.put("MD5", value);
        }
        if (!result.isEmpty()) {
            return result;
        }
        // Google style: x-goog-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.headers().firstValue("x-goog-meta-checksum-sha1").orElse(null);
        if (value != null) {
            result.put("SHA-1", value);
        }
        // Central style: x-goog-meta-checksum-sha1: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.headers().firstValue("x-goog-meta-checksum-md5").orElse(null);
        if (value != null) {
            result.put("MD5", value);
        }

        return result.isEmpty() ? null : result;
    }

    private static Map<String, String> extractNexus2Checksums(HttpResponse<?> response) {
        // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        String etag = response.headers().firstValue("ETag").orElse(null);
        if (etag != null) {
            int start = etag.indexOf("SHA1{"), end = etag.indexOf("}", start + 5);
            if (start >= 0 && end > start) {
                return Collections.singletonMap("SHA-1", etag.substring(start + 5, end));
            }
        }
        return null;
    }

    private static final DateTimeFormatter RFC7231 = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));

    /**
     * Visible for testing.
     */
    static final String HTTP_INSTANCE_KEY_PREFIX = JdkTransporterFactory.class.getName() + ".http.";

    private static HttpClient getOrCreateClient(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {
        final String instanceKey = HTTP_INSTANCE_KEY_PREFIX + repository.getId();

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
                        sslContext = SSLContext.getDefault();
                    }

                    int connectTimeout = ConfigUtils.getInteger(
                            session,
                            ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                            ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                            ConfigurationProperties.CONNECT_TIMEOUT);

                    HttpClient.Builder builder = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofMillis(connectTimeout))
                            .sslContext(sslContext);

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

                    return builder.build();
                } catch (NoSuchAlgorithmException e) {
                    throw new WrapperEx(e);
                }
            });
        } catch (WrapperEx e) {
            throw new NoTransporterException(repository, e.getCause());
        }
    }

    private static final class WrapperEx extends RuntimeException {
        private WrapperEx(Throwable cause) {
            super(cause);
        }
    }
}
