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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer {

    public static class LogEntry {

        public final String method;

        public final String path;

        public final Map<String, String> headers;

        public LogEntry(String method, String path, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
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

    private List<LogEntry> logEntries = Collections.synchronizedList(new ArrayList<LogEntry>());

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
        if (httpsConnector == null) {
            SslContextFactory.Server ssl = new SslContextFactory.Server();
            ssl.setNeedClientAuth(true);
            ssl.setKeyStorePath(new File("src/test/resources/ssl/server-store").getAbsolutePath());
            ssl.setKeyStorePassword("server-pwd");
            ssl.setTrustStorePath(new File("src/test/resources/ssl/client-store").getAbsolutePath());
            ssl.setTrustStorePassword("client-pwd");
            httpsConnector = new ServerConnector(server, ssl);
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

    public HttpServer start() throws Exception {
        if (server != null) {
            return this;
        }

        HandlerList handlers = new HandlerList();
        handlers.addHandler(new LogHandler());
        handlers.addHandler(new ProxyAuthHandler());
        handlers.addHandler(new AuthHandler());
        handlers.addHandler(new RedirectHandler());
        handlers.addHandler(new RepoHandler());

        server = new Server();
        httpConnector = new ServerConnector(server);
        server.addConnector(httpConnector);
        server.setHandler(handlers);
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

    private class LogHandler extends AbstractHandler {

        public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response) {
            LOGGER.info(
                    "{} {}{}",
                    req.getMethod(),
                    req.getRequestURL(),
                    req.getQueryString() != null ? "?" + req.getQueryString() : "");

            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Enumeration<String> en = req.getHeaderNames(); en.hasMoreElements(); ) {
                String name = en.nextElement();
                StringBuilder buffer = new StringBuilder(128);
                for (Enumeration<String> ien = req.getHeaders(name); ien.hasMoreElements(); ) {
                    if (buffer.length() > 0) {
                        buffer.append(", ");
                    }
                    buffer.append(ien.nextElement());
                }
                headers.put(name, buffer.toString());
            }
            logEntries.add(new LogEntry(req.getMethod(), req.getPathInfo(), Collections.unmodifiableMap(headers)));
        }
    }

    private class RepoHandler extends AbstractHandler {

        private final Pattern SIMPLE_RANGE = Pattern.compile("bytes=([0-9])+-");

        public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            String path = req.getPathInfo().substring(1);

            if (!path.startsWith("repo/")) {
                return;
            }
            req.setHandled(true);

            if (ExpectContinue.FAIL.equals(expectContinue) && request.getHeader(HttpHeader.EXPECT.asString()) != null) {
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                return;
            }

            File file = new File(repoDir, path.substring(5));
            if (HttpMethod.GET.is(req.getMethod()) || HttpMethod.HEAD.is(req.getMethod())) {
                if (!file.isFile() || path.endsWith(URIUtil.SLASH)) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                long ifUnmodifiedSince = request.getDateHeader(HttpHeader.IF_UNMODIFIED_SINCE.asString());
                if (ifUnmodifiedSince != -1L && file.lastModified() > ifUnmodifiedSince) {
                    response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return;
                }
                long offset = 0L;
                String range = request.getHeader(HttpHeader.RANGE.asString());
                if (range != null && rangeSupport) {
                    Matcher m = SIMPLE_RANGE.matcher(range);
                    if (m.matches()) {
                        offset = Long.parseLong(m.group(1));
                        if (offset >= file.length()) {
                            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                            return;
                        }
                    }
                    String encoding = request.getHeader(HttpHeader.ACCEPT_ENCODING.asString());
                    if ((encoding != null && !"identity".equals(encoding)) || ifUnmodifiedSince == -1L) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        return;
                    }
                }
                response.setStatus((offset > 0L) ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
                response.setDateHeader(HttpHeader.LAST_MODIFIED.asString(), file.lastModified());
                response.setHeader(HttpHeader.CONTENT_LENGTH.asString(), Long.toString(file.length() - offset));
                if (offset > 0L) {
                    response.setHeader(
                            HttpHeader.CONTENT_RANGE.asString(),
                            "bytes " + offset + "-" + (file.length() - 1L) + "/" + file.length());
                }
                if (checksumHeader != null) {
                    Map<String, Object> checksums = ChecksumUtils.calc(file, Collections.singleton("SHA-1"));
                    if (checksumHeader == ChecksumHeader.NEXUS) {
                        response.setHeader(HttpHeader.ETAG.asString(), "{SHA1{" + checksums.get("SHA-1") + "}}");
                    } else if (checksumHeader == ChecksumHeader.XCHECKSUM) {
                        response.setHeader(
                                "x-checksum-sha1", checksums.get("SHA-1").toString());
                    }
                }
                if (HttpMethod.HEAD.is(req.getMethod())) {
                    return;
                }
                FileInputStream is = null;
                try {
                    is = new FileInputStream(file);
                    if (offset > 0L) {
                        long skipped = is.skip(offset);
                        while (skipped < offset && is.read() >= 0) {
                            skipped++;
                        }
                    }
                    IO.copy(is, response.getOutputStream());
                    is.close();
                    is = null;
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (final IOException e) {
                        // Suppressed due to an exception already thrown in the try block.
                    }
                }
            } else if (HttpMethod.PUT.is(req.getMethod())) {
                if (!webDav) {
                    file.getParentFile().mkdirs();
                }
                if (file.getParentFile().exists()) {
                    try {
                        FileOutputStream os = null;
                        try {
                            os = new FileOutputStream(file);
                            IO.copy(request.getInputStream(), os);
                            os.close();
                            os = null;
                        } finally {
                            try {
                                if (os != null) {
                                    os.close();
                                }
                            } catch (final IOException e) {
                                // Suppressed due to an exception already thrown in the try block.
                            }
                        }
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
                    response.setHeader("DAV", "1,2");
                }
                response.setHeader(HttpHeader.ALLOW.asString(), "GET, PUT, HEAD, OPTIONS");
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
        }
    }

    private class RedirectHandler extends AbstractHandler {

        public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response) {
            String path = req.getPathInfo();
            if (!path.startsWith("/redirect/")) {
                return;
            }
            req.setHandled(true);
            StringBuilder location = new StringBuilder(128);
            String scheme = req.getParameter("scheme");
            location.append(scheme != null ? scheme : req.getScheme());
            location.append("://");
            location.append(req.getServerName());
            location.append(":");
            if ("http".equalsIgnoreCase(scheme)) {
                location.append(getHttpPort());
            } else if ("https".equalsIgnoreCase(scheme)) {
                location.append(getHttpsPort());
            } else {
                location.append(req.getServerPort());
            }
            location.append("/repo").append(path.substring(9));
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader(HttpHeader.LOCATION.asString(), location.toString());
        }
    }

    private class AuthHandler extends AbstractHandler {

        public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            if (ExpectContinue.BROKEN.equals(expectContinue)
                    && "100-continue".equalsIgnoreCase(request.getHeader(HttpHeader.EXPECT.asString()))) {
                request.getInputStream();
            }

            if (username != null && password != null) {
                if (checkBasicAuth(request.getHeader(HttpHeader.AUTHORIZATION.asString()), username, password)) {
                    return;
                }
                req.setHandled(true);
                response.setHeader(HttpHeader.WWW_AUTHENTICATE.asString(), "basic realm=\"Test-Realm\"");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }
    }

    private class ProxyAuthHandler extends AbstractHandler {

        public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response) {
            if (proxyUsername != null && proxyPassword != null) {
                if (checkBasicAuth(
                        request.getHeader(HttpHeader.PROXY_AUTHORIZATION.asString()), proxyUsername, proxyPassword)) {
                    return;
                }
                req.setHandled(true);
                response.setHeader(HttpHeader.PROXY_AUTHENTICATE.asString(), "basic realm=\"Test-Realm\"");
                response.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
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
                    credentials = B64Code.decode(credentials, StringUtil.__ISO_8859_1);
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
