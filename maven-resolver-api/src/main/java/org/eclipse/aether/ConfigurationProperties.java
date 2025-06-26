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

    /**
     * Prefix for all configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_AETHER = "aether.";

    /**
     * Prefix for repository system related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_SYSTEM = PREFIX_AETHER + "system.";

    /**
     * Prefix for sync context related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_SYNC_CONTEXT = PREFIX_AETHER + "syncContext.";

    /**
     * Prefix for connector related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_CONNECTOR = PREFIX_AETHER + "connector.";

    /**
     * Prefix for layout related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_LAYOUT = PREFIX_AETHER + "layout.";

    /**
     * Prefix for checksum related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_CHECKSUMS = PREFIX_AETHER + "checksums.";

    /**
     * Prefix for local repository manager related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_LRM = PREFIX_AETHER + "lrm.";

    /**
     * Prefix for generator related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_GENERATOR = PREFIX_AETHER + "generator.";

    /**
     * Prefix for util related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.10
     */
    public static final String PREFIX_UTIL = PREFIX_AETHER + "util.";

    /**
     * Prefix for transport related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_TRANSPORT = PREFIX_AETHER + "transport.";

    /**
     * Prefix for HTTP protocol related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_TRANSPORT_HTTP = PREFIX_TRANSPORT + "http.";

    /**
     * Prefix for HTTPS protocol related configurations. <em>For internal use only.</em>
     *
     * @since 2.0.0
     */
    public static final String PREFIX_TRANSPORT_HTTPS = PREFIX_TRANSPORT + "https.";

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
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_IMPLICIT_PRIORITIES}
     * @configurationRepoIdSuffix No
     */
    public static final String IMPLICIT_PRIORITIES = PREFIX_PRIORITY + "implicit";

    /**
     * The default extension priority mode if {@link #IMPLICIT_PRIORITIES} isn't set.
     */
    public static final boolean DEFAULT_IMPLICIT_PRIORITIES = false;

    /**
     * A flag indicating whether the created ordered components should be cached in session.
     *
     * @since 2.0.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_CACHED_PRIORITIES}
     * @configurationRepoIdSuffix No
     */
    public static final String CACHED_PRIORITIES = PREFIX_PRIORITY + "cached";

    /**
     * The priority to use for a certain extension class. {@code &lt;class&gt;} can either be the fully qualified
     * name or the simple name of a class. If the class name ends with Factory that suffix could optionally be left out.
     * This configuration is used by {@code org.eclipse.aether.internal.impl.PrioritizedComponents} internal utility
     * to sort classes by priority. This is reusable utility (so an extension can make use of it), but by default
     * in "vanilla" Resolver following classes are sorted:
     * <ul>
     *     <li>{@code org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory}</li>
     *     <li>{@code org.eclipse.aether.spi.connector.RepositoryConnectorFactory}</li>
     *     <li>{@code org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory}</li>
     *     <li>{@code org.eclipse.aether.spi.connector.transport.TransporterFactory}</li>
     *     <li>{@code org.eclipse.aether.spi.artifact.decorator.ArtifactDecoratorFactory}</li>
     *     <li>{@code org.eclipse.aether.spi.artifact.generator.ArtifactGeneratorFactory}</li>
     *     <li>{@code org.eclipse.aether.impl.MetadataGeneratorFactory}</li>
     * </ul>
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Float}
     * @configurationRepoIdSuffix No
     */
    public static final String CLASS_PRIORITIES = PREFIX_PRIORITY + "<class>";

    /**
     * The default caching of priority components if {@link #CACHED_PRIORITIES} isn't set. Default value is {@code true}.
     *
     * @since 2.0.0
     */
    public static final boolean DEFAULT_CACHED_PRIORITIES = true;

    /**
     * A flag indicating whether interaction with the user is allowed.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_INTERACTIVE}
     * @configurationRepoIdSuffix No
     */
    public static final String INTERACTIVE = PREFIX_AETHER + "interactive";

    /**
     * The default interactive mode if {@link #INTERACTIVE} isn't set.
     */
    public static final boolean DEFAULT_INTERACTIVE = false;

    /**
     * The user agent that repository connectors should report to servers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_USER_AGENT}
     * @configurationRepoIdSuffix No
     */
    public static final String USER_AGENT = PREFIX_TRANSPORT_HTTP + "userAgent";

    /**
     * The default user agent to use if {@link #USER_AGENT} isn't set.
     */
    public static final String DEFAULT_USER_AGENT = "Aether";

    /**
     * The maximum amount of time (in milliseconds) to wait for a successful connection to a remote server. Non-positive
     * values indicate no timeout.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_CONNECT_TIMEOUT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONNECT_TIMEOUT = PREFIX_TRANSPORT_HTTP + "connectTimeout";

    /**
     * The default connect timeout to use if {@link #CONNECT_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 30 * 1000;

    /**
     * The maximum amount of time (in milliseconds) to wait for remaining data to arrive from a remote server. Note that
     * this timeout does not restrict the overall duration of a request, it only restricts the duration of inactivity
     * between consecutive data packets. Non-positive values indicate no timeout.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_REQUEST_TIMEOUT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String REQUEST_TIMEOUT = PREFIX_TRANSPORT_HTTP + "requestTimeout";

    /**
     * The default request timeout to use if {@link #REQUEST_TIMEOUT} isn't set.
     */
    public static final int DEFAULT_REQUEST_TIMEOUT = 1800 * 1000;

    /**
     * The request headers to use for HTTP-based repository connectors. The headers are specified using a
     * {@code Map<String, String>}, mapping a header name to its value. Besides this general key, clients may also
     * specify headers for a specific remote repository by appending the suffix {@code .&lt;repoId&gt;} to this key when
     * storing the headers map. The repository-specific headers map is supposed to be complete, i.e. is not merged with
     * the general headers map.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.util.Map}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_HEADERS = PREFIX_TRANSPORT_HTTP + "headers";

    /**
     * The encoding/charset to use when exchanging credentials with HTTP servers. Besides this general key, clients may
     * also specify the encoding for a specific remote repository by appending the suffix {@code .&lt;repoId&gt;} to this key
     * when storing the charset name.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_CREDENTIAL_ENCODING}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_CREDENTIAL_ENCODING = PREFIX_TRANSPORT_HTTP + "credentialEncoding";

    /**
     * The default encoding/charset to use if {@link #HTTP_CREDENTIAL_ENCODING} isn't set.
     */
    public static final String DEFAULT_HTTP_CREDENTIAL_ENCODING = "ISO-8859-1";

    /**
     * The maximum number of times a request to a remote server should be retried in case of an error.
     *
     * @since 1.9.6
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_RETRY_HANDLER_COUNT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_RETRY_HANDLER_COUNT = PREFIX_TRANSPORT_HTTP + "retryHandler.count";

    /**
     * The default number of retries to use if {@link #HTTP_RETRY_HANDLER_COUNT} isn't set.
     *
     * @since 1.9.6
     */
    public static final int DEFAULT_HTTP_RETRY_HANDLER_COUNT = 3;

    /**
     * The initial retry interval in millis of request to a remote server should be waited in case of
     * "too many requests" (HTTP codes 429 and 503). Accepts long as milliseconds. This value is used if remote server
     * does not use {@code Retry-After} header, in which case Server value is obeyed.
     *
     * @since 1.9.16
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_RETRY_HANDLER_INTERVAL}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_RETRY_HANDLER_INTERVAL = PREFIX_TRANSPORT_HTTP + "retryHandler.interval";

    /**
     * The default initial retry interval to use if {@link #HTTP_RETRY_HANDLER_INTERVAL} isn't set.
     * Default value 5000ms.
     *
     * @since 1.9.16
     */
    public static final long DEFAULT_HTTP_RETRY_HANDLER_INTERVAL = 5000L;

    /**
     * The maximum retry interval in millis of request to a remote server above which the request should be aborted
     * instead. In theory, a malicious server could tell Maven "come back after 100 years" that would stall the build
     * for some. Using this parameter Maven will fail the request instead, if interval is above this value.
     *
     * @since 1.9.16
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_RETRY_HANDLER_INTERVAL_MAX}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_RETRY_HANDLER_INTERVAL_MAX = PREFIX_TRANSPORT_HTTP + "retryHandler.intervalMax";

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
     * @since 1.9.16
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE =
            PREFIX_TRANSPORT_HTTP + "retryHandler.serviceUnavailable";

    /**
     * The default HTTP codes of remote server responses that should be handled as "too many requests".
     * Default value: "429,503".
     *
     * @since 1.9.16
     */
    public static final String DEFAULT_HTTP_RETRY_HANDLER_SERVICE_UNAVAILABLE = "429,503";

    /**
     * Should HTTP client use preemptive-authentication for all HTTP verbs (works only w/ BASIC). By default, is
     * disabled, as it is considered less secure.
     *
     * @since 1.9.6
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_PREEMPTIVE_AUTH}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_PREEMPTIVE_AUTH = PREFIX_TRANSPORT_HTTP + "preemptiveAuth";

    /**
     * The default value to use if {@link #HTTP_PREEMPTIVE_AUTH} isn't set (false).
     *
     * @since 1.9.6
     */
    public static final boolean DEFAULT_HTTP_PREEMPTIVE_AUTH = false;

    /**
     * Should HTTP client reuse connections (in other words, pool connections) or not?
     *
     * @since 1.9.8
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_REUSE_CONNECTIONS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_REUSE_CONNECTIONS = PREFIX_TRANSPORT_HTTP + "reuseConnections";

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
     * @since 1.9.8
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_CONNECTION_MAX_TTL}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_CONNECTION_MAX_TTL = PREFIX_TRANSPORT_HTTP + "connectionMaxTtl";

    /**
     * The default value to use if {@link #HTTP_CONNECTION_MAX_TTL} isn't set (300 seconds).
     *
     * @since 1.9.8
     */
    public static final int DEFAULT_HTTP_CONNECTION_MAX_TTL = 300;

    /**
     * The maximum concurrent connections per route HTTP client is allowed to use.
     *
     * @since 1.9.8
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_MAX_CONNECTIONS_PER_ROUTE = PREFIX_TRANSPORT_HTTP + "maxConnectionsPerRoute";

    /**
     * The default value to use if {@link #HTTP_MAX_CONNECTIONS_PER_ROUTE} isn't set (50 connections).
     *
     * @since 1.9.8
     */
    public static final int DEFAULT_HTTP_MAX_CONNECTIONS_PER_ROUTE = 50;

    /**
     * The local address (interface) to use with HTTP transport. Not all transport supports this option.
     *
     * @since 2.0.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_LOCAL_ADDRESS = PREFIX_TRANSPORT_HTTP + "localAddress";

    /**
     * Boolean flag should the HTTP transport support WebDAV remote. Not all transport support this option.
     *
     * @since 2.0.0 (moved out from maven-resolver-transport-http).
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_SUPPORT_WEBDAV}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_SUPPORT_WEBDAV = PREFIX_TRANSPORT_HTTP + "supportWebDav";

    /**
     * Default value to use if {@link #HTTP_SUPPORT_WEBDAV} is not set: {@code false}.
     *
     * @since 2.0.0
     */
    public static final boolean DEFAULT_HTTP_SUPPORT_WEBDAV = false;

    /**
     * Boolean flag should the HTTP transport use preemptive-auth for PUT requests. Not all transport support this
     * option.
     *
     * @since 2.0.0 (moved out from maven-resolver-transport-http).
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_PREEMPTIVE_PUT_AUTH}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_PREEMPTIVE_PUT_AUTH = PREFIX_TRANSPORT_HTTP + "preemptivePutAuth";

    /**
     * Default value if {@link #HTTP_PREEMPTIVE_PUT_AUTH} is not set: {@code true}.
     *
     * @since 2.0.0
     */
    public static final boolean DEFAULT_HTTP_PREEMPTIVE_PUT_AUTH = true;

    /**
     * Boolean flag should the HTTP transport use expect-continue handshake for PUT requests. Not all transport support
     * this option. This option may be needed for some broken HTTP servers. Default value corresponds to given
     * transport default one (resolver does not override those), but if configuration IS given, it will replace
     * given transport own default value.
     *
     * @since 1.9.17
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTP_EXPECT_CONTINUE = PREFIX_TRANSPORT_HTTP + "expectContinue";

    /**
     * The mode that sets HTTPS transport "security mode": to ignore any SSL errors (certificate validity checks,
     * hostname verification). The default value is {@link #HTTPS_SECURITY_MODE_DEFAULT}.
     *
     * @see #HTTPS_SECURITY_MODE_DEFAULT
     * @see #HTTPS_SECURITY_MODE_INSECURE
     * @since 1.9.6
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #HTTPS_SECURITY_MODE_DEFAULT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String HTTPS_SECURITY_MODE = PREFIX_TRANSPORT_HTTPS + "securityMode";

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
     * A flag indicating which visitor should be used to "flatten" the dependency graph into list. Default is
     * same as in older resolver versions "preOrder", while it can accept values like "postOrder" and "levelOrder".
     *
     * @see #REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_PREORDER
     * @see #REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_POSTORDER
     * @see #REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_LEVELORDER
     * @since 2.0.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_PREORDER}
     * @configurationRepoIdSuffix No
     */
    public static final String REPOSITORY_SYSTEM_DEPENDENCY_VISITOR = PREFIX_SYSTEM + "dependencyVisitor";

    /**
     * The visitor strategy "preOrder".
     *
     * @since 2.0.0
     */
    public static final String REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_PREORDER = "preOrder";

    /**
     * The visitor strategy "postOrder". This was the only one supported in Resolver 1.x and is hence the
     * default as well.
     *
     * @since 2.0.0
     */
    public static final String REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_POSTORDER = "postOrder";

    /**
     * The visitor strategy "levelOrder".
     *
     * @since 2.0.0
     */
    public static final String REPOSITORY_SYSTEM_DEPENDENCY_VISITOR_LEVELORDER = "levelOrder";

    /**
     * A flag indicating whether version scheme cache statistics should be printed on JVM shutdown.
     * This is useful for analyzing cache performance and effectiveness in development and testing scenarios.
     *
     * @since 2.0.10
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_VERSION_SCHEME_CACHE_DEBUG}
     * @configurationRepoIdSuffix No
     */
    public static final String VERSION_SCHEME_CACHE_DEBUG = PREFIX_UTIL + "versionScheme.cacheDebug";

    /**
     * The default value for version scheme cache debug if {@link #VERSION_SCHEME_CACHE_DEBUG} isn't set.
     *
     * @since 2.0.10
     */
    public static final boolean DEFAULT_VERSION_SCHEME_CACHE_DEBUG = false;

    private ConfigurationProperties() {
        // hide constructor
    }
}
