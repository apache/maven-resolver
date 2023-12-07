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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
import org.eclipse.aether.spi.connector.transport.http.HttpTransporter;
import org.eclipse.aether.spi.connector.transport.http.HttpTransporterException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.client.http.HttpClientConnectionFactory;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.PathRequestContent;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.ClientConnectionFactoryOverHTTP2;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transporter for HTTP/HTTPS.
 *
 * @since 2.0.0
 */
final class JettyTransporter extends AbstractTransporter implements HttpTransporter {
    private static final int MULTIPLE_CHOICES = 300;

    private static final int NOT_FOUND = 404;

    private static final int PRECONDITION_FAILED = 412;

    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    private static final String CONTENT_LENGTH = "Content-Length";

    private static final String CONTENT_RANGE = "Content-Range";

    private static final String LAST_MODIFIED = "Last-Modified";

    private static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    private static final String RANGE = "Range";

    private static final String USER_AGENT = "User-Agent";

    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile("\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*");

    private final URI baseUri;

    private final HttpClient client;

    private final int requestTimeout;

    private final Map<String, String> headers;

    JettyTransporter(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException {
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

        this.headers = headers;

        this.requestTimeout = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT);

        this.client = getOrCreateClient(session, repository);
    }

    private URI resolve(TransportTask task) {
        return baseUri.resolve(task.getLocation());
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
        Request request = client.newRequest(resolve(task))
                .timeout(requestTimeout, TimeUnit.MILLISECONDS)
                .method("HEAD");
        request.headers(m -> headers.forEach(m::add));
        Response response = request.send();
        if (response.getStatus() >= MULTIPLE_CHOICES) {
            throw new HttpTransporterException(response.getStatus());
        }
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = task.getResumeOffset() > 0L && task.getDataFile() != null;
        Response response;
        InputStreamResponseListener listener;

        while (true) {
            Request request = client.newRequest(resolve(task))
                    .timeout(requestTimeout, TimeUnit.MILLISECONDS)
                    .method("GET");
            request.headers(m -> headers.forEach(m::add));

            if (resume) {
                long resumeOffset = task.getResumeOffset();
                request.headers(h -> {
                    h.add(RANGE, "bytes=" + resumeOffset + '-');
                    h.addDateField(IF_UNMODIFIED_SINCE, task.getDataFile().lastModified() - MODIFICATION_THRESHOLD);
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
                throw new HttpTransporterException(response.getStatus());
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
        final File dataFile = task.getDataFile();
        if (dataFile == null) {
            try (InputStream is = listener.getInputStream()) {
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
                try (InputStream is = listener.getInputStream()) {
                    utilGet(task, is, true, length, downloadResumed);
                }
                tempFile.move();
            } finally {
                task.setDataFile(dataFile);
            }
        }
        if (task.getDataFile() != null && response.getHeaders().getDateField(LAST_MODIFIED) != -1) {
            long lastModified =
                    response.getHeaders().getDateField(LAST_MODIFIED); // note: Wagon also does first not last
            if (lastModified != -1) {
                try {
                    Files.setLastModifiedTime(task.getDataFile().toPath(), FileTime.fromMillis(lastModified));
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

    private static Map<String, String> extractXChecksums(Response response) {
        String value;
        HashMap<String, String> result = new HashMap<>();
        // Central style: x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.getHeaders().get("x-checksum-sha1");
        if (value != null) {
            result.put("SHA-1", value);
        }
        // Central style: x-checksum-md5: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.getHeaders().get("x-checksum-md5");
        if (value != null) {
            result.put("MD5", value);
        }
        if (!result.isEmpty()) {
            return result;
        }
        // Google style: x-goog-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.getHeaders().get("x-goog-meta-checksum-sha1");
        if (value != null) {
            result.put("SHA-1", value);
        }
        // Central style: x-goog-meta-checksum-sha1: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.getHeaders().get("x-goog-meta-checksum-md5");
        if (value != null) {
            result.put("MD5", value);
        }

        return result.isEmpty() ? null : result;
    }

    private static Map<String, String> extractNexus2Checksums(Response response) {
        // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        String etag = response.getHeaders().get("ETag");
        if (etag != null) {
            int start = etag.indexOf("SHA1{"), end = etag.indexOf("}", start + 5);
            if (start >= 0 && end > start) {
                return Collections.singletonMap("SHA-1", etag.substring(start + 5, end));
            }
        }
        return null;
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        Request request = client.newRequest(resolve(task)).method("PUT").timeout(requestTimeout, TimeUnit.MILLISECONDS);
        request.headers(m -> headers.forEach(m::add));
        try (FileUtils.TempFile tempFile = FileUtils.newTempFile()) {
            utilPut(task, Files.newOutputStream(tempFile.getPath()), true);
            request.body(new PathRequestContent(tempFile.getPath()));

            Response response;
            try {
                response = request.send();
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof Exception) {
                    throw (Exception) t;
                } else {
                    throw new RuntimeException(t);
                }
            }
            if (response.getStatus() >= MULTIPLE_CHOICES) {
                throw new HttpTransporterException(response.getStatus());
            }
        }
    }

    @Override
    protected void implClose() {
        // noop
    }

    /**
     * Visible for testing.
     */
    static final String JETTY_INSTANCE_KEY_PREFIX = JettyTransporterFactory.class.getName() + ".jetty.";

    static final Logger LOGGER = LoggerFactory.getLogger(JettyTransporter.class);

    @SuppressWarnings("checkstyle:methodlength")
    private static HttpClient getOrCreateClient(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException {

        final String instanceKey = JETTY_INSTANCE_KEY_PREFIX + repository.getId();

        try {
            return (HttpClient) session.getData().computeIfAbsent(instanceKey, () -> {
                SSLContext sslContext = null;
                BasicAuthentication basicAuthentication = null;
                try {
                    try (AuthenticationContext repoAuthContext =
                            AuthenticationContext.forRepository(session, repository)) {
                        if (repoAuthContext != null) {
                            sslContext = repoAuthContext.get(AuthenticationContext.SSL_CONTEXT, SSLContext.class);

                            String username = repoAuthContext.get(AuthenticationContext.USERNAME);
                            String password = repoAuthContext.get(AuthenticationContext.PASSWORD);

                            basicAuthentication = new BasicAuthentication(
                                    URI.create(repository.getUrl()), Authentication.ANY_REALM, username, password);
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

                    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
                    sslContextFactory.setSslContext(sslContext);

                    ClientConnector clientConnector = new ClientConnector();
                    clientConnector.setSslContextFactory(sslContextFactory);

                    HTTP2Client http2Client = new HTTP2Client(clientConnector);
                    ClientConnectionFactoryOverHTTP2.HTTP2 http2 =
                            new ClientConnectionFactoryOverHTTP2.HTTP2(http2Client);

                    HttpClientTransportDynamic transport;
                    if ("https".equalsIgnoreCase(repository.getProtocol())) {
                        transport = new HttpClientTransportDynamic(
                                clientConnector, http2, HttpClientConnectionFactory.HTTP11); // HTTPS, prefer H2
                    } else {
                        transport = new HttpClientTransportDynamic(
                                clientConnector,
                                HttpClientConnectionFactory.HTTP11,
                                http2); // plaintext HTTP, H2 cannot be used
                    }

                    HttpClient httpClient = new HttpClient(transport);
                    httpClient.setConnectTimeout(connectTimeout);
                    httpClient.setFollowRedirects(true);
                    httpClient.setMaxRedirects(2);

                    httpClient.setUserAgentField(null); // we manage it

                    if (basicAuthentication != null) {
                        httpClient.getAuthenticationStore().addAuthentication(basicAuthentication);
                    }

                    if (repository.getProxy() != null) {
                        HttpProxy proxy = new HttpProxy(
                                repository.getProxy().getHost(),
                                repository.getProxy().getPort());

                        httpClient.getProxyConfiguration().addProxy(proxy);
                        try (AuthenticationContext proxyAuthContext =
                                AuthenticationContext.forProxy(session, repository)) {
                            if (proxyAuthContext != null) {
                                String username = proxyAuthContext.get(AuthenticationContext.USERNAME);
                                String password = proxyAuthContext.get(AuthenticationContext.PASSWORD);

                                BasicAuthentication proxyAuthentication = new BasicAuthentication(
                                        proxy.getURI(), Authentication.ANY_REALM, username, password);

                                httpClient.getAuthenticationStore().addAuthentication(proxyAuthentication);
                            }
                        }
                    }
                    if (!session.addOnSessionEndedHandler(() -> {
                        try {
                            httpClient.stop();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })) {
                        LOGGER.warn(
                                "Using Resolver 2 feature without Resolver 2 session handling, you may leak resources.");
                    }
                    httpClient.start();
                    return httpClient;
                } catch (Exception e) {
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
