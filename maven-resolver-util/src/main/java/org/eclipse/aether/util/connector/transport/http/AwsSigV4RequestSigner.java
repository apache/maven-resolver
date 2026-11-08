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
package org.eclipse.aether.util.connector.transport.http;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;

/**
 * {@link HttpRequestSigner} for <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html">AWS
 * Signature Version 4</a>, as S3 and S3-compatible object stores expect it. The payload is declared as
 * {@code UNSIGNED-PAYLOAD}, which S3 accepts and which keeps the transports from reading uploads twice; the
 * signed headers are {@code host}, {@code x-amz-date}, {@code x-amz-content-sha256} and, with a session token,
 * {@code x-amz-security-token}. The request path is signed as sent, without re-encoding or normalization, which is
 * what S3 requires.
 *
 * @since 2.0.26
 */
public final class AwsSigV4RequestSigner implements HttpRequestSigner {
    /**
     * The {@code x-amz-content-sha256} value declaring that the payload is not part of the signature.
     */
    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private static final String HEADER_AUTHORIZATION = "Authorization";

    private static final String HEADER_HOST = "host";

    private static final String HEADER_AMZ_DATE = "x-amz-date";

    private static final String HEADER_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";

    private static final String HEADER_AMZ_SECURITY_TOKEN = "x-amz-security-token";

    private static final Set<String> SIGNED_HEADER_NAMES;

    static {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.add(HEADER_AUTHORIZATION);
        names.add(HEADER_AMZ_DATE);
        names.add(HEADER_AMZ_CONTENT_SHA256);
        names.add(HEADER_AMZ_SECURITY_TOKEN);
        SIGNED_HEADER_NAMES = Collections.unmodifiableSet(names);
    }

    private static final DateTimeFormatter AMZ_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String accessKey;

    /**
     * {@code "AWS4"} followed by the secret key, in UTF-8: the only form of the secret the signer keeps, so that
     * {@link #close()} can wipe it.
     */
    private final byte[] signingKeySeed;

    private volatile boolean closed;

    private final String sessionToken;

    private final String region;

    private final String service;

    private final Clock clock;

    /**
     * Creates a signer for the given credentials, region and service, signing with the system UTC clock.
     *
     * @param accessKey the access key id, never {@code null}
     * @param secretKey the secret access key, never {@code null}; the signer copies it, so the caller may wipe it
     * @param sessionToken the session token of temporary credentials, may be {@code null}
     * @param region the region to sign for, never {@code null}
     * @param service the service to sign for, for S3 {@code s3}, never {@code null}
     */
    public AwsSigV4RequestSigner(
            String accessKey, char[] secretKey, String sessionToken, String region, String service) {
        this(accessKey, secretKey, sessionToken, region, service, Clock.systemUTC());
    }

    AwsSigV4RequestSigner(
            String accessKey, char[] secretKey, String sessionToken, String region, String service, Clock clock) {
        this.accessKey = requireNonNull(accessKey, "accessKey");
        this.signingKeySeed = signingKeySeed(requireNonNull(secretKey, "secretKey"));
        this.sessionToken = sessionToken;
        this.region = requireNonNull(region, "region");
        this.service = requireNonNull(service, "service");
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public Map<String, String> sign(String method, URI uri) {
        requireNonNull(method, "method");
        requireNonNull(uri, "uri");
        if (closed) {
            throw new IllegalStateException("The signer is closed");
        }
        String amzDate = AMZ_DATE_FORMAT.format(clock.instant());
        String dateStamp = amzDate.substring(0, 8);

        // canonical headers: lower-case names, sorted, trimmed values
        TreeMap<String, String> signedHeaders = new TreeMap<>();
        signedHeaders.put(HEADER_HOST, hostHeader(uri));
        signedHeaders.put(HEADER_AMZ_CONTENT_SHA256, UNSIGNED_PAYLOAD);
        signedHeaders.put(HEADER_AMZ_DATE, amzDate);
        if (sessionToken != null) {
            signedHeaders.put(HEADER_AMZ_SECURITY_TOKEN, sessionToken);
        }
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaderNames = new StringBuilder();
        for (Map.Entry<String, String> header : signedHeaders.entrySet()) {
            canonicalHeaders
                    .append(header.getKey())
                    .append(':')
                    .append(header.getValue().trim())
                    .append('\n');
            if (signedHeaderNames.length() > 0) {
                signedHeaderNames.append(';');
            }
            signedHeaderNames.append(header.getKey());
        }

        String canonicalRequest = method.toUpperCase(java.util.Locale.ENGLISH)
                + '\n'
                + canonicalPath(uri)
                + '\n'
                + canonicalQuery(uri)
                + '\n'
                + canonicalHeaders
                + '\n'
                + signedHeaderNames
                + '\n'
                + UNSIGNED_PAYLOAD;
        String scope = dateStamp + '/' + region + '/' + service + "/aws4_request";
        String stringToSign = ALGORITHM + '\n' + amzDate + '\n' + scope + '\n' + hex(sha256(canonicalRequest));

        byte[] signingKey = hmac(hmac(hmac(hmac(signingKeySeed, dateStamp), region), service), "aws4_request");
        String signature = hex(hmac(signingKey, stringToSign));

        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put(HEADER_AMZ_DATE, amzDate);
        result.put(HEADER_AMZ_CONTENT_SHA256, UNSIGNED_PAYLOAD);
        if (sessionToken != null) {
            result.put(HEADER_AMZ_SECURITY_TOKEN, sessionToken);
        }
        result.put(
                HEADER_AUTHORIZATION,
                ALGORITHM + " Credential=" + accessKey + '/' + scope + ", SignedHeaders=" + signedHeaderNames
                        + ", Signature=" + signature);
        return result;
    }

    @Override
    public Set<String> signedHeaderNames() {
        return SIGNED_HEADER_NAMES;
    }

    /**
     * The {@code Host} header value HTTP clients send for the URI: the host, plus the port only when it is explicit
     * and not the scheme's default.
     */
    static String hostHeader(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            String authority = uri.getRawAuthority();
            if (authority == null) {
                throw new IllegalArgumentException("URI has no host: " + uri);
            }
            int at = authority.lastIndexOf('@');
            return at >= 0 ? authority.substring(at + 1) : authority;
        }
        int port = uri.getPort();
        int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        if (port == -1 || port == defaultPort) {
            return host;
        }
        return host + ':' + port;
    }

    /**
     * S3 canonicalizes the request path by decoding it and URI-encoding each segment once, so the path has to be
     * signed in that form rather than as the client sends it: clients differ in what they leave unencoded (the
     * Apache client sends a literal {@code +}, others {@code %2B}) and S3 accepts all of them as the same path.
     */
    static String canonicalPath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        StringBuilder canonical = new StringBuilder(path.length());
        int start = 0;
        while (true) {
            int slash = path.indexOf('/', start);
            String segment = slash < 0 ? path.substring(start) : path.substring(start, slash);
            canonical.append(encodeSegment(decode(segment)));
            if (slash < 0) {
                break;
            }
            canonical.append('/');
            start = slash + 1;
        }
        return canonical.toString();
    }

    private static String decode(String segment) {
        if (segment.indexOf('%') < 0) {
            return segment;
        }
        byte[] out = new byte[segment.length()];
        int n = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                int hi = Character.digit(segment.charAt(i + 1), 16);
                int lo = Character.digit(segment.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out[n++] = (byte) ((hi << 4) + lo);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            if (n + bytes.length > out.length) {
                out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, n + bytes.length));
            }
            System.arraycopy(bytes, 0, out, n, bytes.length);
            n += bytes.length;
        }
        return new String(out, 0, n, StandardCharsets.UTF_8);
    }

    private static String encodeSegment(String segment) {
        StringBuilder out = new StringBuilder(segment.length() + 8);
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.'
                    || c == '~') {
                out.append((char) c);
            } else {
                out.append('%')
                        .append(Character.toUpperCase(HEX[c >>> 4]))
                        .append(Character.toUpperCase(HEX[c & 0x0f]));
            }
        }
        return out.toString();
    }

    private static String canonicalQuery(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return "";
        }
        List<String[]> parameters = new ArrayList<>();
        for (String parameter : query.split("&")) {
            int eq = parameter.indexOf('=');
            parameters.add(
                    eq >= 0
                            ? new String[] {parameter.substring(0, eq), parameter.substring(eq + 1)}
                            : new String[] {parameter, ""});
        }
        parameters.sort((a, b) -> {
            int c = a[0].compareTo(b[0]);
            return c != 0 ? c : a[1].compareTo(b[1]);
        });
        StringBuilder canonical = new StringBuilder();
        for (String[] parameter : parameters) {
            if (canonical.length() > 0) {
                canonical.append('&');
            }
            canonical.append(parameter[0]).append('=').append(parameter[1]);
        }
        return canonical.toString();
    }

    private static byte[] sha256(String data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Wipes the key material; the signer refuses to sign afterwards.
     */
    @Override
    public void close() {
        closed = true;
        Arrays.fill(signingKeySeed, (byte) 0);
    }

    private static byte[] signingKeySeed(char[] secretKey) {
        char[] chars = new char[4 + secretKey.length];
        "AWS4".getChars(0, 4, chars, 0);
        System.arraycopy(secretKey, 0, chars, 4, secretKey.length);
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] seed = new byte[encoded.remaining()];
        encoded.get(seed);
        Arrays.fill(chars, '\0');
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return seed;
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(out);
    }
}
