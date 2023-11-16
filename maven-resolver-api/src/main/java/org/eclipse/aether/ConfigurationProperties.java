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
package org.eclipse.aether;

/**
 * The keys and defaults for common configuration properties.
 *
 * @see RepositorySystemSession#getConfigProperties()
 */
public final class ConfigurationProperties {

    private static final String PREFIX_AETHER = "aether.";

    private static final String PREFIX_CONNECTOR = PREFIX_AETHER + "connector.";

    /**
     * The prefix for properties that control the priority of pluggable extensions like transporters. For example, for
     * an extension with the fully qualified class name "org.eclipse.MyExtensionFactory", the configuration properties
     * "aether.priority.org.eclipse.MyExtensionFactory", "aether.priority.MyExtensionFactory" and
     * "aether.priority.MyExtension" will be consulted for the priority, in that order (obviously, the last key is only
     * tried if the class name ends with "Factory"). The corresponding value is a float and the special value
     * {@link Float#NaN} or "NaN" (case-sensitive) can be used to disable the extension.
     */
    public static final String PREFIX_PRIORITY = PREFIX_AETHER + "priority.";

    /**
     * A flag indicating whether the priorities of pluggable extensions are implicitly given by their iteration order
     * such that the first extension has the highest priority. If set, an extension's built-in priority as well as any
     * corresponding {@code aether.priority.*} configuration properties are ignored when searching for a suitable
     * implementation among the available extensions. This priority mode is meant for cases where the application will
     * present/inject extensions in the desired search order.
     *
     * @see #DEFAULT_IMPLICIT_PRIORITIES
     */
    public static final String IMPLICIT_PRIORITIES = PREFIX_PRIORITY + "implicit";

    /**
     * The default extension priority mode if {@link #IMPLICIT_PRIORITIES} isn't set.
     */
    public static final boolean DEFAULT_IMPLICIT_PRIORITIES = false;

    /**
     * A flag indicating whether interaction with the user is allowed.
     *
     * @see #DEFAULT_INTERACTIVE
     */
    public static final String INTERACTIVE = PREFIX_AETHER + "interactive";

    /**
     * The default interactive mode if {@link #INTERACTIVE} isn't set.
     */
    public static final boolean DEFAULT_INTERACTIVE = false;

    /**
     * The user agent that repository connectors should report to servers.
     *
     * @see #DEFAULT_USER_AGENT
     */
    public static final String USER_AGENT = PREFIX_CONNECTOR + "userAgent";

    /**
     * The default user agent to use if {@link #USER_AGENT} isn't set.
     */
    public static final String DEFAULT_USER_AGENT = "Aether";

    /**
     * The maximum amount of time (in milliseconds) to wait for a successful connection to a remote server. Non-positive
     * values indicate no timeout.
     *
     * @see #DEFAULT_CONNECT_TIMEOUT
     */
    public static final String CONNECT_TIMEOUT = PREFIX_CONNECTOR + "connectTimeout";

    /**
     * The default connect timeout to use if {@link #CONNECT_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 10 * 1000;

    /**
     * The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote server. Note that
     * this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity
     * between consecutive data packets. Non-positive values indicate no timeout.
     *
     * @see #DEFAULT_REQUEST_TIMEOUT
     */
    public static final String REQUEST_TIMEOUT = PREFIX_CONNECTOR + "requestTimeout";

    /**
     * The default request timeout to use if {@link #REQUEST_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_REQUEST_TIMEOUT = 1800 * 1000;

    /**
     * The request headers to use for HTTP-based repository connectors. The headers are specified using a
     * {@code Map<String, String>}, mapping a header name to its value. Besides this general key, clients may also
     * specify headers for a specific remote repository by appending the suffix {@code .<repoId>} to this key when
     * storing the headers map. The repository-specific headers map is supposed to be complete, i.e. is not merged with
     * the general headers map.
     */
    public static final String HTTP_HEADERS = PREFIX_CONNECTOR + "http.headers";

    /**
     * The encoding/charset to use when exchanging credentials with HTTP servers. Besides this general key, clients may
     * also specify the encoding for a specific remote repository by appending the suffix {@code .<repoId>} to this key
     * when storing the charset name.
     *
     * @see #DEFAULT_HTTP_CREDENTIAL_ENCODING
     */
    public static final String HTTP_CREDENTIAL_ENCODING = PREFIX_CONNECTOR + "http.credentialEncoding";

    /**
     * The default encoding/charset to use if {@link #HTTP_CREDENTIAL_ENCODING} isn't set.
     */
    public static final String DEFAULT_HTTP_CREDENTIAL_ENCODING = "ISO-8859-1";

    /**
     * The maximum number of times a request to a remote server should be retried in case of an error.
     *
     * @see #DEFAULT_HTTP_RETRY_HANDLER_COUNT
     * @since 1.9.6
     */
    public static final String HTTP_RETRY_HANDLER_COUNT = PREFIX_CONNECTOR + "http.retryHandler.count";

    /**
     * The default number of retries to use if {@link #HTTP_RETRY_HANDLER_COUNT} isn't set.
     *
     * @since 1.9.6
     */
    public static final int DEFAULT_HTTP_RETRY_HANDLER_COUNT = 3;

    /**
     * The initial retry interval of request to a remote server should be waited in case of "too many requests"
     * (HTTP codes 429 and 503). Accepts long as milliseconds. This value is used if remote server does not use
     * {@code Retry-After} header, in which case Server value is obeyed.
     *
     * @see #DEFAULT_HTTP_RETRY_HANDLER_INTERVAL
     * @since 1.9.16
     */
    public static final String HTTP_RETRY_HANDLER_INTERVAL = PREFIX_CONNECTOR + "http.retryHandler.interval";

    /**
     * The default initial retry interval to use if {@link #HTTP_RETRY_HANDLER_INTERVAL} isn't set.
     * Default value 5000ms.
     *
     * @since 1.9.16
     */
    public static final long DEFAULT_HTTP_RETRY_HANDLER_INTERVAL = 5000L;

    /**
     * The maximum retry interval of request to a remote server above which the request should be aborted instead.
     * In theory, a malicious server could tell Maven "come back after 100 years" that would stall the build for
     * some. Using this parameter Maven will fail the request instead, if interval is above this value.
     *
     * @see #DEFAULT_HTTP_RETRY_HANDLER_INTERVAL_MAX
     * @since 1.9.16
     */
    public static final String HTTP_RETRY_HANDLER_INTERVAL_MAX = PREFIX_CONNECTOR + "http.retryHandler.intervalMax";

    /**
     * The default retry interval maximum to use if {@link #HTTP_RETRY_HANDLER_INTERVAL_MAX} isn't set.
     * Default value 5 minutes.
     *
     * @since 1.9.16
     */
    public static final long DEFAULT_HTTP_RETRY_HANDLER_INTERVAL_MAX = 300_000L;

    /**
     * The HTTP codes of remote server responses that should be handled as "too many requests"
     * (examples: HTTP codes 429 and 503). Accepts comma separated list of HTTP response codes.
     *
     * @see #DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE
     * @since 1.9.16
     */
    public static final String HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE =
            PREFIX_CONNECTOR + "http.retryHandler.serviceUnavailable";

    /**
     * The default HTTP codes of remote server responses that should be handled as "too many requests".
     * Default value: "429,503".
     *
     * @since 1.9.16
     */
    public static final String DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE = "429,503";

    /**
     * Should HTTP client use preemptive auth (w/ BASIC) or not?
     *
     * @see #DEFAULT_HTTP_PREEMPTIVE_AUTH
     * @since 1.9.6
     */
    public static final String HTTP_PREEMPTIVE_AUTH = PREFIX_CONNECTOR + "http.preemptiveAuth";

    /**
     * The default value to use if {@link #HTTP_PREEMPTIVE_AUTH} isn't set (false).
     *
     * @since 1.9.6
     */
    public static final boolean DEFAULT_HTTP_PREEMPTIVE_AUTH = false;

    /**
     * Should HTTP client reuse connections (in other words, pool connections) or not?
     *
     * @see #DEFAULT_HTTP_REUSE_CONNECTIONS
     * @since 1.9.8
     */
    public static final String HTTP_REUSE_CONNECTIONS = PREFIX_CONNECTOR + "http.reuseConnections";

    /**
     * The default value to use if {@link #HTTP_REUSE_CONNECTIONS} isn't set (true).
     *
     * @since 1.9.8
     */
    public static final boolean DEFAULT_HTTP_REUSE_CONNECTIONS = true;

    /**
     * Total time to live in seconds for an HTTP connection, after that time, the connection will be dropped
     * (no matter for how long it was idle).
     *
     * @see #DEFAULT_HTTP_CONNECTION_MAX_TTL
     * @since 1.9.8
     */
    public static final String HTTP_CONNECTION_MAX_TTL = PREFIX_CONNECTOR + "http.connectionMaxTtl";

    /**
     * The default value to use if {@link #HTTP_CONNECTION_MAX_TTL} isn't set (300 seconds).
     *
     * @since 1.9.8
     */
    public static final int DEFAULT_HTTP_CONNECTION_MAX_TTL = 300;

    /**
     * The maximum concurrent connections per route HTTP client is allowed to use.
     *
     * @see #DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE
     * @since 1.9.8
     */
    public static final String HTTP_MAX_CONNECTIONS_PER_ROUTE = PREFIX_CONNECTOR + "http.maxConnectionsPerRoute";

    /**
     * The default value to use if {@link #HTTP_MAX_CONNECTIONS_PER_ROUTE} isn't set (50 connections).
     *
     * @since 1.9.8
     */
    public static final int DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE = 50;

    /**
     * Boolean flag should the HTTP transport use expect-continue handshake for PUT requests. Not all transport support
     * this option. This option may be needed for some broken HTTP servers.
     *
     * @see #DEFAULT_HTTP_EXPECT_CONTINUE
     * @since 1.9.17
     */
    public static final String HTTP_EXPECT_CONTINUE = PREFIX_CONNECTOR + "http.expectContinue";

    /**
     * Default value if {@link #HTTP_EXPECT_CONTINUE} is not set: {@code true}.
     *
     * @since 1.9.17
     */
    public static final boolean DEFAULT_HTTP_EXPECT_CONTINUE = true;

    /**
     * The mode that sets HTTPS transport "security mode": to ignore any SSL errors (certificate validity checks,
     * hostname verification). The default value is {@link #HTTPS_SECURITY_MODE_DEFAULT}.
     *
     * @see #HTTPS_SECURITY_MODE_DEFAULT
     * @see #HTTPS_SECURITY_MODE_INSECURE
     * @since 1.9.6
     */
    public static final String HTTPS_SECURITY_MODE = PREFIX_CONNECTOR + "https.securityMode";

    /**
     * The default HTTPS security mode.
     *
     * @since 1.9.6
     */
    public static final String HTTPS_SECURITY_MODE_DEFAULT = "default";

    /**
     * The insecure HTTPS security mode (certificate validation, hostname verification are all ignored).
     *
     * @since 1.9.6
     */
    public static final String HTTPS_SECURITY_MODE_INSECURE = "insecure";

    /**
     * A flag indicating whether checksums which are retrieved during checksum validation should be persisted in the
     * local filesystem next to the file they provide the checksum for.
     *
     * @see #DEFAULT_PERSISTED_CHECKSUMS
     */
    public static final String PERSISTED_CHECKSUMS = PREFIX_CONNECTOR + "persistedChecksums";

    /**
     * The default checksum persistence mode if {@link #PERSISTED_CHECKSUMS} isn't set.
     */
    public static final boolean DEFAULT_PERSISTED_CHECKSUMS = true;

    private ConfigurationProperties() {
        // hide constructor
    }
}
