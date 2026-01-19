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
package org.eclipse.aether.internal.test.util.http;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;
import org.eclipse.aether.spi.connector.transport.http.RFC9457.RFC9457Payload;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.compression.server.CompressionConfig;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.pathmap.MatchedResource;
import org.eclipse.jetty.http.pathmap.PathMappings;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.Blocker;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer {

    public static class LogEntry {

        private final String method;

        private final String path;

        private final Map<String, String> requestHeaders;

        private Map<String, String> responseHeaders;

        CountDownLatch responseHeadersAvailableSignal = new CountDownLatch(1);

        public LogEntry(String method, String path, Map<String, String> requestHeaders) {
            this.method = method;
            this.path = path;
            this.requestHeaders = requestHeaders;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public Map<String, String> getRequestHeaders() {
            return requestHeaders;
        }

        /**
         * This method blocks until the response headers are available.
         * @return the response headers
         */
        public Map<String, String> getResponseHeaders() {
            try {
                if (!responseHeadersAvailableSignal.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout waiting for response headers to be available");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for response headers to be available", e);
            }
            return responseHeaders;
        }

        public void setResponseHeaders(Map<String, String> responseHeaders) {
            this.responseHeaders = responseHeaders;
            responseHeadersAvailableSignal.countDown();
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    public enum ExpectContinue {
        FAIL,
        PROPER,
        BROKEN
    }

    public enum ChecksumHeader {
        NEXUS,
        XCHECKSUM
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);

    private File repoDir;

    private boolean rangeSupport = true;

    private boolean webDav;

    private ExpectContinue expectContinue = ExpectContinue.PROPER;

    private ChecksumHeader checksumHeader;

    private Server server;

    private ServerConnector httpConnector;

    private ServerConnector httpsConnector;

    private String username;

    private String password;

    private String proxyUsername;

    private String proxyPassword;

    private final AtomicInteger connectionsToClose = new AtomicInteger(0);

    private final AtomicInteger serverErrorsBeforeWorks = new AtomicInteger(0);

    private int serverErrorStatusCode;

    private final List<LogEntry> logEntries = Collections.synchronizedList(new ArrayList<>());

    public String getHost() {
        return "localhost";
    }

    public int getHttpPort() {
        return httpConnector != null ? httpConnector.getLocalPort() : -1;
    }

    public int getHttpsPort() {
        return httpsConnector != null ? httpsConnector.getLocalPort() : -1;
    }

    public String getHttpUrl() {
        return "http://" + getHost() + ":" + getHttpPort();
    }

    public String getHttpsUrl() {
        return "https://" + getHost() + ":" + getHttpsPort();
    }

    public HttpServer addSslConnector() {
        return addSslConnector(true, true);
    }

    public HttpServer addSelfSignedSslConnector() {
        return addSslConnector(false, true);
    }

    public HttpServer addSelfSignedSslConnectorHttp2Only() {
        return addSslConnector(false, false);
    }

    private HttpServer addSslConnector(boolean needClientAuth, boolean needHttp11) {
        if (httpsConnector == null) {
            SslContextFactory.Server ssl = new SslContextFactory.Server();
            ssl.setNeedClientAuth(needClientAuth);
            if (!needClientAuth) {
                ssl.setKeyStorePath(HttpTransporterTest.KEY_STORE_SELF_SIGNED_PATH
                        .toAbsolutePath()
                        .toString());
                ssl.setKeyStorePassword("server-pwd");
                ssl.setSniRequired(false);
            } else {
                ssl.setKeyStorePath(
                        HttpTransporterTest.KEY_STORE_PATH.toAbsolutePath().toString());
                ssl.setKeyStorePassword("server-pwd");
                ssl.setTrustStorePath(
                        HttpTransporterTest.TRUST_STORE_PATH.toAbsolutePath().toString());
                ssl.setTrustStorePassword("client-pwd");
                ssl.setSniRequired(false);
            }

            HttpConfiguration httpsConfig = new HttpConfiguration();
            SecureRequestCustomizer customizer = new SecureRequestCustomizer();
            customizer.setSniHostCheck(false);
            httpsConfig.addCustomizer(customizer);

            HttpConnectionFactory http1 = null;
            if (needHttp11) {
                http1 = new HttpConnectionFactory(httpsConfig);
            }

            HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(httpsConfig);

            ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
            alpn.setDefaultProtocol(http1 != null ? http1.getProtocol() : http2.getProtocol());

            SslConnectionFactory tls = new SslConnectionFactory(ssl, alpn.getProtocol());
            if (http1 != null) {
                httpsConnector = new ServerConnector(server, tls, alpn, http2, http1);
            } else {
                httpsConnector = new ServerConnector(server, tls, alpn, http2);
            }
            server.addConnector(httpsConnector);
            try {
                httpsConnector.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return this;
    }

    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    public HttpServer setRepoDir(File repoDir) {
        this.repoDir = repoDir;
        return this;
    }

    public HttpServer setRangeSupport(boolean rangeSupport) {
        this.rangeSupport = rangeSupport;
        return this;
    }

    public HttpServer setWebDav(boolean webDav) {
        this.webDav = webDav;
        return this;
    }

    public HttpServer setExpectSupport(ExpectContinue expectContinue) {
        this.expectContinue = expectContinue;
        return this;
    }

    public HttpServer setChecksumHeader(ChecksumHeader checksumHeader) {
        this.checksumHeader = checksumHeader;
        return this;
    }

    public HttpServer setAuthentication(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public HttpServer setProxyAuthentication(String username, String password) {
        proxyUsername = username;
        proxyPassword = password;
        return this;
    }

    public HttpServer setConnectionsToClose(int connectionsToClose) {
        this.connectionsToClose.set(connectionsToClose);
        return this;
    }

    public HttpServer setServerErrorsBeforeWorks(int serverErrorsBeforeWorks) {
        return setServerErrorsBeforeWorks(serverErrorsBeforeWorks, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public HttpServer setServerErrorsBeforeWorks(int serverErrorsBeforeWorks, int errorStatusCode) {
        this.serverErrorsBeforeWorks.set(serverErrorsBeforeWorks);
        this.serverErrorStatusCode = errorStatusCode;
        return this;
    }

    public HttpServer start() throws Exception {
        if (server != null) {
            return this;
        }

        server = new Server();
        httpConnector = new ServerConnector(server);
        server.addConnector(httpConnector);

        server.setHandler(new LogHandler(new CompressionEnforcingHandler(new Handler.Sequence(
                new ConnectionClosingHandler(),
                new ServerErrorHandler(),
                new ProxyAuthHandler(),
                new AuthHandler(),
                new RedirectHandler(),
                new RepoHandler(),
                new RFC9457Handler()))));
        server.start();

        return this;
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
            httpConnector = null;
            httpsConnector = null;
        }
    }

    private class CompressionEnforcingHandler extends CompressionHandler {
        // duplicate of CompressionHandler.pathConfigs which is private
        private final PathMappings<CompressionConfig> pathConfigs = new PathMappings<>();

        CompressionEnforcingHandler(Handler handler) {
            super(handler);
            this.putConfiguration(
                    "/br/*",
                    CompressionConfig.builder().compressIncludeEncoding("br").build());
            this.putConfiguration(
                    "/zstd/*",
                    CompressionConfig.builder().compressIncludeEncoding("zstd").build());
            this.putConfiguration(
                    "/gzip/*",
                    CompressionConfig.builder().compressIncludeEncoding("gzip").build());
            this.putConfiguration(
                    "/deflate/*",
                    CompressionConfig.builder()
                            .compressIncludeEncoding("deflate")
                            .build());
        }

        @Override
        public CompressionConfig putConfiguration(PathSpec pathSpec, CompressionConfig config) {
            // deliberately not set it in the super class yet
            return pathConfigs.put(pathSpec, config);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            Handler next = getHandler();
            if (next == null) {
                return false;
            }
            String pathInContext = Request.getPathInContext(request);
            MatchedResource<CompressionConfig> matchedConfig = this.pathConfigs.getMatched(pathInContext);
            if (matchedConfig == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("skipping compression: path {} has no matching compression config", pathInContext);
                }
                // No configuration, skip
                return next.handle(request, response, callback);
            }

            // set the matched config in the super class for further processing, but for all paths
            // no need to reset it later as this handler is not used among multiple requests
            super.putConfiguration(PathSpec.from("/*"), matchedConfig.getResource());
            // first path segment determines the encoding, remove it from the request path for further processing
            return super.handle(new StripLeadingPathSegmentsRequestWrapper(request, 1), response, callback);
        }
    }

    private static class StripLeadingPathSegmentsRequestWrapper extends Request.Wrapper {
        private final HttpURI modifiedURI;

        StripLeadingPathSegmentsRequestWrapper(Request wrapped, int segmentsToStrip) {
            super(wrapped);
            this.modifiedURI = stripPathSegments(wrapped.getHttpURI(), segmentsToStrip);
        }

        private static HttpURI stripPathSegments(HttpURI originalURI, int segmentsToStrip) {
            if (segmentsToStrip <= 0) {
                return originalURI;
            }

            String originalPath = originalURI.getPath();
            if (originalPath == null || originalPath.isEmpty()) {
                return originalURI;
            }

            // Split path into segments
            String[] segments = originalPath.split("/");
            StringBuilder newPath = new StringBuilder();

            // Skip empty first segment (from leading /) and the specified number of segments
            int skipCount = 0;
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].isEmpty() && i == 0) {
                    // Skip leading empty segment from leading /
                    continue;
                }
                if (skipCount < segmentsToStrip) {
                    skipCount++;
                    continue;
                }
                newPath.append("/").append(segments[i]);
            }

            // If we stripped everything, return root path
            if (newPath.isEmpty()) {
                newPath.append("/");
            }

            // Build new URI with modified path
            return org.eclipse.jetty.http.HttpURI.build(originalURI)
                    .path(newPath.toString())
                    .asImmutable();
        }

        @Override
        public HttpURI getHttpURI() {
            return modifiedURI;
        }
    }

    private class ConnectionClosingHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            if (connectionsToClose.getAndDecrement() > 0) {
                request.getConnectionMetaData().getConnection().close();
            }
            return false;
        }
    }

    private class ServerErrorHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) throws IOException {
            if (serverErrorsBeforeWorks.getAndDecrement() > 0) {
                response.setStatus(serverErrorStatusCode);
                writeResponseBodyMessage(request, response, "Oops, come back later!");
                return true;
            }
            return false;
        }
    }

    private class LogHandler extends Handler.Wrapper {

        LogHandler(Handler handler) {
            super(handler);
        }

        @Override
        public boolean handle(Request req, Response response, Callback callback) throws Exception {

            LOGGER.info(
                    "{} {}{}",
                    req.getMethod(),
                    req.getHttpURI().getDecodedPath(),
                    req.getHttpURI().getQuery() != null ? "?" + req.getHttpURI().getQuery() : "");

            Map<String, String> requestHeaders =
                    toUnmodifiableMap(req.getHeaders()); // capture request headers before other handlers modify them
            LogEntry logEntry = new LogEntry(req.getMethod(), req.getHttpURI().getPathQuery(), requestHeaders);
            logEntries.add(logEntry);
            // prevent closing the response before logging (assume all writes are synchronous for simplicity)
            boolean result = super.handle(req, response, callback);
            // capture response headers after other handlers modified them
            // at this point in time the connection may have been already closed (i.e. last chunk already sent)
            logEntry.setResponseHeaders(toUnmodifiableMap(response.getHeaders()));
            if (result) {
                callback.succeeded();
            }
            return result;
        }

        Map<String, String> toUnmodifiableMap(HttpFields headers) {
            Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (HttpField header : headers) {
                map.put(header.getName(), header.getValueList().stream().collect(Collectors.joining(", ")));
            }
            return Collections.unmodifiableMap(map);
        }
    }

    private static final Pattern SIMPLE_RANGE = Pattern.compile("bytes=([0-9])+-");

    private class RepoHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request req, Response response, Callback callback) throws Exception {
            String path = req.getHttpURI().getDecodedPath().substring(1);

            if (!path.startsWith("repo/")) {
                return false;
            }

            if (ExpectContinue.FAIL.equals(expectContinue) && req.getHeaders().get(HttpHeader.EXPECT) != null) {
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                writeResponseBodyMessage(req, response, "Expectation was set to fail");
                return true;
            }

            File file = new File(repoDir, path.substring(5));
            if (HttpMethod.GET.is(req.getMethod()) || HttpMethod.HEAD.is(req.getMethod())) {
                if (!file.isFile() || path.endsWith("/")) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    writeResponseBodyMessage(req, response, "Not found");
                    return true;
                }
                long ifUnmodifiedSince = req.getHeaders().getDateField(HttpHeader.IF_UNMODIFIED_SINCE);
                if (ifUnmodifiedSince != -1L && file.lastModified() > ifUnmodifiedSince) {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                    writeResponseBodyMessage(req, response, "Precondition failed");
                    return true;
                }
                long offset = 0L;
                String range = req.getHeaders().get(HttpHeader.RANGE);
                if (range != null && rangeSupport) {
                    Matcher m = SIMPLE_RANGE.matcher(range);
                    if (m.matches()) {
                        offset = Long.parseLong(m.group(1));
                        if (offset >= file.length()) {
                            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                            writeResponseBodyMessage(req, response, "Range not satisfiable");
                            return true;
                        }
                    }
                    String encoding = req.getHeaders().get(HttpHeader.ACCEPT_ENCODING);
                    if ((encoding != null && !"identity".equals(encoding)) || ifUnmodifiedSince == -1L) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        return true;
                    }
                }
                response.setStatus((offset > 0L) ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
                response.getHeaders().add(HttpHeader.LAST_MODIFIED, DateGenerator.formatDate(file.lastModified()));
                response.getHeaders().add(HttpHeader.CONTENT_LENGTH, Long.toString(file.length() - offset));
                if (offset > 0L) {
                    response.getHeaders()
                            .add(
                                    HttpHeader.CONTENT_RANGE,
                                    "bytes " + offset + "-" + (file.length() - 1L) + "/" + file.length());
                }
                if (checksumHeader != null) {
                    Map<String, String> checksums = ChecksumAlgorithmHelper.calculate(
                            file, Collections.singletonList(new Sha1ChecksumAlgorithmFactory()));
                    if (checksumHeader == ChecksumHeader.NEXUS) {
                        response.getHeaders().add(HttpHeader.ETAG.asString(), "{SHA1{" + checksums.get("SHA-1") + "}}");
                    } else if (checksumHeader == ChecksumHeader.XCHECKSUM) {
                        response.getHeaders().add("x-checksum-sha1", checksums.get(Sha1ChecksumAlgorithmFactory.NAME));
                    }
                }
                if (HttpMethod.HEAD.is(req.getMethod())) {
                    return true;
                }
                Content.Source contentSource =
                        Content.Source.from(new ByteBufferPool.Sized(null), file.toPath(), offset, -1);
                try (Blocker.Callback fileReadCallback = Blocker.callback()) {
                    Content.copy(contentSource, response, fileReadCallback);
                    fileReadCallback.block();
                }
            } else if (HttpMethod.PUT.is(req.getMethod())) {
                if (!webDav) {
                    file.getParentFile().mkdirs();
                }
                if (file.getParentFile().exists()) {
                    try (SeekableByteChannel channel = Files.newByteChannel(
                                    file.toPath(),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.TRUNCATE_EXISTING);
                            Blocker.Callback fileWriteCallback = Blocker.callback()) {
                        Content.copy(req, Content.Sink.from(channel), fileWriteCallback);
                        fileWriteCallback.block();
                    } catch (IOException e) {
                        file.delete();
                        throw e;
                    }
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }
            } else if (HttpMethod.OPTIONS.is(req.getMethod())) {
                if (webDav) {
                    response.getHeaders().add("DAV", "1,2");
                }
                response.getHeaders().add(HttpHeader.ALLOW, "GET, PUT, HEAD, OPTIONS");
                response.setStatus(HttpServletResponse.SC_OK);
            } else if (webDav && "MKCOL".equals(req.getMethod())) {
                if (file.exists()) {
                    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                } else if (file.mkdir()) {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                } else {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                }
            } else {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
            return true;
        }
    }

    private void writeResponseBodyMessage(Request request, Response response, String message) throws IOException {
        // write synchronously to avoid closing the response too early
        try (Blocker.Callback callback = Blocker.callback()) {
            Content.Sink.write(response, false, message, callback);
            callback.block();
        }
    }

    private class RFC9457Handler extends Handler.Abstract {
        @Override
        public boolean handle(Request req, Response response, Callback callback) throws Exception {
            String path = req.getHttpURI().getPath().substring(1);

            if (!path.startsWith("rfc9457/")) {
                return false;
            }

            if (HttpMethod.GET.is(req.getMethod())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getHeaders().add(HttpHeader.CONTENT_TYPE.asString(), "application/problem+json");
                RFC9457Payload rfc9457Payload;
                if (path.endsWith("missing_fields.txt")) {
                    rfc9457Payload = new RFC9457Payload(null, null, null, null, null);
                } else {
                    rfc9457Payload = new RFC9457Payload(
                            URI.create("https://example.com/probs/out-of-credit"),
                            HttpServletResponse.SC_FORBIDDEN,
                            "You do not have enough credit.",
                            "Your current balance is 30, but that costs 50.",
                            URI.create("/account/12345/msgs/abc"));
                }
                writeResponseBodyMessage(req, response, buildRFC9457Message(rfc9457Payload));
            }
            return true;
        }
    }

    private String buildRFC9457Message(RFC9457Payload payload) {
        return new Gson().toJson(payload, RFC9457Payload.class);
    }

    private class RedirectHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request req, Response response, Callback callback) throws Exception {
            String path = req.getHttpURI().getPath();
            if (!path.startsWith("/redirect/")) {
                return false;
            }
            StringBuilder location = new StringBuilder(128);
            String scheme = Request.getParameters(req).getValue("scheme");
            location.append(scheme != null ? scheme : req.getHttpURI().getScheme());
            location.append("://");
            location.append(Request.getServerName(req));
            location.append(":");
            if ("http".equalsIgnoreCase(scheme)) {
                location.append(getHttpPort());
            } else if ("https".equalsIgnoreCase(scheme)) {
                location.append(getHttpsPort());
            } else {
                location.append(Request.getServerPort(req));
            }
            location.append("/repo").append(path.substring(9));
            Response.sendRedirect(
                    req, response, callback, HttpServletResponse.SC_MOVED_PERMANENTLY, location.toString(), false);
            return true;
        }
    }

    private class AuthHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            if (ExpectContinue.BROKEN.equals(expectContinue)
                    && "100-continue".equalsIgnoreCase(request.getHeaders().get(HttpHeader.EXPECT))) {
                // TODO: what is this for?
                Request.asInputStream(request);
            }

            if (username != null && password != null) {
                if (checkBasicAuth(request.getHeaders().get(HttpHeader.AUTHORIZATION), username, password)) {
                    return false;
                }
                response.getHeaders().add(HttpHeader.WWW_AUTHENTICATE, "Basic realm=\"Test-Realm\"");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return true;
            }
            return false;
        }
    }

    private class ProxyAuthHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request req, Response response, Callback callback) throws Exception {
            if (proxyUsername != null && proxyPassword != null) {
                if (checkBasicAuth(
                        req.getHeaders().get(HttpHeader.PROXY_AUTHORIZATION), proxyUsername, proxyPassword)) {
                    return false;
                }
                response.getHeaders().add(HttpHeader.PROXY_AUTHENTICATE, "basic realm=\"Test-Realm\"");
                response.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
                return true;
            } else {
                return false;
            }
        }
    }

    static boolean checkBasicAuth(String credentials, String username, String password) {
        if (credentials != null) {
            int space = credentials.indexOf(' ');
            if (space > 0) {
                String method = credentials.substring(0, space);
                if ("basic".equalsIgnoreCase(method)) {
                    credentials = credentials.substring(space + 1);
                    credentials = new String(Base64.getDecoder().decode(credentials), StandardCharsets.ISO_8859_1);
                    int i = credentials.indexOf(':');
                    if (i > 0) {
                        String user = credentials.substring(0, i);
                        String pass = credentials.substring(i + 1);
                        if (username.equals(user) && password.equals(pass)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
