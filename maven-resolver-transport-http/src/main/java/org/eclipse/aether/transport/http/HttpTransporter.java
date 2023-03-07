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
package org.eclipse.aether.transport.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
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
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * A transporter for HTTP/HTTPS.
 */
final class HttpTransporter extends AbstractTransporter {

    static final String SUPPORT_WEBDAV = "aether.connector.http.supportWebDav";

    static final String PREEMPTIVE_PUT_AUTH = "aether.connector.http.preemptivePutAuth";

    static final String USE_SYSTEM_PROPERTIES = "aether.connector.http.useSystemProperties";

    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile("\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*");

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTransporter.class);

    private final Map<String, ChecksumExtractor> checksumExtractors;

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

    HttpTransporter(
            Map<String, ChecksumExtractor> checksumExtractors,
            RemoteRepository repository,
            RepositorySystemSession session)
            throws NoTransporterException {
        if (!"http".equalsIgnoreCase(repository.getProtocol()) && !"https".equalsIgnoreCase(repository.getProtocol())) {
            throw new NoTransporterException(repository);
        }
        this.checksumExtractors = requireNonNull(checksumExtractors, "checksum extractors must not be null");
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
        this.state = new LocalState(session, repository, new SslConfig(session, repoAuthContext, httpsSecurityMode));

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
        this.preemptivePutAuth = // defaults to true: Wagon does same
                ConfigUtils.getBoolean(
                        session, true, PREEMPTIVE_PUT_AUTH + "." + repository.getId(), PREEMPTIVE_PUT_AUTH);
        this.supportWebDav = // defaults to false: who needs it will enable it
                ConfigUtils.getBoolean(session, false, SUPPORT_WEBDAV + "." + repository.getId(), SUPPORT_WEBDAV);
        String credentialEncoding = ConfigUtils.getString(
                session,
                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "." + repository.getId(),
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING);
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
        int retryCount = ConfigUtils.getInteger(
                session,
                ConfigurationProperties.DEFAULT_HTTP_RETRY_HANDLER_COUNT,
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT + "." + repository.getId(),
                ConfigurationProperties.HTTP_RETRY_HANDLER_COUNT);
        String userAgent = ConfigUtils.getString(
                session, ConfigurationProperties.DEFAULT_USER_AGENT, ConfigurationProperties.USER_AGENT);

        Charset credentialsCharset = Charset.forName(credentialEncoding);

        Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory(credentialsCharset))
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory(credentialsCharset))
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
                .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
                .build();

        SocketConfig socketConfig =
                SocketConfig.custom().setSoTimeout(requestTimeout).build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectTimeout)
                .setSocketTimeout(requestTimeout)
                .build();

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler(retryCount, false);

        HttpClientBuilder builder = HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(retryHandler)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setConnectionManager(state.getConnectionManager())
                .setConnectionManagerShared(true)
                .setDefaultCredentialsProvider(toCredentialsProvider(server, repoAuthContext, proxy, proxyAuthContext))
                .setProxy(proxy);

        final boolean useSystemProperties = ConfigUtils.getBoolean(
                session, false, USE_SYSTEM_PROPERTIES + "." + repository.getId(), USE_SYSTEM_PROPERTIES);
        if (useSystemProperties) {
            LOGGER.warn(
                    "Transport used Apache HttpClient is instructed to use system properties: this may yield in unwanted side-effects. Please use documented means to configure transport.");
            LOGGER.warn(
                    "Please use documented means to configure resolver transport!");
            builder.useSystemProperties();
        }

        this.client = builder.build();
    }

    private static HttpHost toHost(Proxy proxy) {
        HttpHost host = null;
        if (proxy != null) {
            host = new HttpHost(proxy.getHost(), proxy.getPort());
        }
        return host;
    }

    private static CredentialsProvider toCredentialsProvider(
            HttpHost server, AuthenticationContext serverAuthCtx, HttpHost proxy, AuthenticationContext proxyAuthCtx) {
        CredentialsProvider provider = toCredentialsProvider(server.getHostName(), AuthScope.ANY_PORT, serverAuthCtx);
        if (proxy != null) {
            CredentialsProvider p = toCredentialsProvider(proxy.getHostName(), proxy.getPort(), proxyAuthCtx);
            provider = new DemuxCredentialsProvider(provider, p, proxy);
        }
        return provider;
    }

    private static CredentialsProvider toCredentialsProvider(String host, int port, AuthenticationContext ctx) {
        DeferredCredentialsProvider provider = new DeferredCredentialsProvider();
        if (ctx != null) {
            AuthScope basicScope = new AuthScope(host, port);
            provider.setCredentials(basicScope, new DeferredCredentialsProvider.BasicFactory(ctx));

            AuthScope ntlmScope = new AuthScope(host, port, AuthScope.ANY_REALM, "ntlm");
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
    public int classify(Throwable error) {
        if (error instanceof HttpResponseException
                && ((HttpResponseException) error).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        HttpHead request = commonHeaders(new HttpHead(resolve(task)));
        execute(request, null);
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        boolean resume = true;
        boolean applyChecksumExtractors = true;

        EntityGetter getter = new EntityGetter(task);
        HttpGet request = commonHeaders(new HttpGet(resolve(task)));
        while (true) {
            try {
                if (resume) {
                    resume(request, task);
                }
                if (applyChecksumExtractors) {
                    for (ChecksumExtractor checksumExtractor : checksumExtractors.values()) {
                        checksumExtractor.prepareRequest(request);
                    }
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
                if (applyChecksumExtractors) {
                    boolean retryWithoutExtractors = false;
                    for (ChecksumExtractor checksumExtractor : checksumExtractors.values()) {
                        if (checksumExtractor.retryWithoutExtractor(e)) {
                            retryWithoutExtractors = true;
                            break;
                        }
                    }
                    if (retryWithoutExtractors) {
                        request = commonHeaders(new HttpGet(resolve(task)));
                        applyChecksumExtractors = false;
                        continue;
                    }
                }
                throw e;
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
                request = commonHeaders(entity(new HttpPut(request.getURI()), entity));
                execute(request, null);
                return;
            }
            throw e;
        }
    }

    private void execute(HttpUriRequest request, EntityGetter getter) throws Exception {
        try {
            SharingHttpContext context = new SharingHttpContext(state);
            prepare(request, context);
            try (CloseableHttpResponse response = client.execute(server, request, context)) {
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

    private void prepare(HttpUriRequest request, SharingHttpContext context) {
        final boolean put = HttpPut.METHOD_NAME.equalsIgnoreCase(request.getMethod());
        if (preemptiveAuth || (preemptivePutAuth && put)) {
            state.setAuthScheme(server, new BasicScheme());
        }
        if (supportWebDav) {
            if (state.getWebDav() == null && (put || isPayloadPresent(request))) {
                HttpOptions req = commonHeaders(new HttpOptions(request.getURI()));
                try (CloseableHttpResponse response = client.execute(server, req, context)) {
                    state.setWebDav(response.containsHeader(HttpHeaders.DAV));
                    EntityUtils.consumeQuietly(response.getEntity());
                } catch (IOException e) {
                    LOGGER.debug("Failed to prepare HTTP context", e);
                }
            }
            if (put && Boolean.TRUE.equals(state.getWebDav())) {
                mkdirs(request.getURI(), context);
            }
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void mkdirs(URI uri, SharingHttpContext context) {
        List<URI> dirs = UriUtils.getDirectories(baseUri, uri);
        int index = 0;
        for (; index < dirs.size(); index++) {
            try (CloseableHttpResponse response =
                    client.execute(server, commonHeaders(new HttpMkCol(dirs.get(index))), context)) {
                try {
                    int status = response.getStatusLine().getStatusCode();
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

    private <T extends HttpEntityEnclosingRequest> T entity(T request, HttpEntity entity) {
        request.setEntity(entity);
        return request;
    }

    private boolean isPayloadPresent(HttpUriRequest request) {
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            return entity != null && entity.getContentLength() != 0;
        }
        return false;
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

    @SuppressWarnings("checkstyle:magicnumber")
    private <T extends HttpUriRequest> T resume(T request, GetTask task) {
        long resumeOffset = task.getResumeOffset();
        if (resumeOffset > 0L && task.getDataFile() != null) {
            request.setHeader(HttpHeaders.RANGE, "bytes=" + resumeOffset + '-');
            request.setHeader(
                    HttpHeaders.IF_UNMODIFIED_SINCE,
                    DateUtils.formatDate(new Date(task.getDataFile().lastModified() - 60L * 1000L)));
            request.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
        }
        return request;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void handleStatus(CloseableHttpResponse response) throws HttpResponseException {
        int status = response.getStatusLine().getStatusCode();
        if (status >= 300) {
            throw new HttpResponseException(status, response.getStatusLine().getReasonPhrase() + " (" + status + ")");
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

        public void handle(CloseableHttpResponse response) throws IOException, TransferCancelledException {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                entity = new ByteArrayEntity(new byte[0]);
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
            final File dataFile = task.getDataFile();
            if (dataFile == null) {
                try (InputStream is = entity.getContent()) {
                    utilGet(task, is, true, length, resume);
                    extractChecksums(response);
                }
            } else {
                try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(dataFile.toPath())) {
                    task.setDataFile(tempFile.getPath().toFile(), resume);
                    if (resume && Files.isRegularFile(dataFile.toPath())) {
                        try (InputStream inputStream = Files.newInputStream(dataFile.toPath())) {
                            Files.copy(inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    try (InputStream is = entity.getContent()) {
                        utilGet(task, is, true, length, resume);
                    }
                    tempFile.move();
                } finally {
                    task.setDataFile(dataFile);
                }
            }
            extractChecksums(response);
        }

        private void extractChecksums(CloseableHttpResponse response) {
            for (Map.Entry<String, ChecksumExtractor> extractorEntry : checksumExtractors.entrySet()) {
                Map<String, String> checksums = extractorEntry.getValue().extractChecksums(response);
                if (checksums != null) {
                    checksums.forEach(task::setChecksum);
                    return;
                }
            }
        }
    }

    private class PutTaskEntity extends AbstractHttpEntity {

        private final PutTask task;

        PutTaskEntity(PutTask task) {
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
    }
}
