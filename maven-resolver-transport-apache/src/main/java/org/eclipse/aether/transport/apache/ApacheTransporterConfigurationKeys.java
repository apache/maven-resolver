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
package org.eclipse.aether.transport.apache;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for Apache Transport.
 *
 * @since 2.0.0
 */
public final class ApacheTransporterConfigurationKeys {
    private ApacheTransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + ApacheTransporterFactory.NAME + ".";

    /**
     * If enabled, underlying Apache HttpClient will use system properties as well to configure itself (typically
     * used to set up HTTP Proxy via Java system properties). See HttpClientBuilder for used properties. This mode
     * is not recommended, better use documented ways of configuration instead.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_USE_SYSTEM_PROPERTIES}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_USE_SYSTEM_PROPERTIES = CONFIG_PROPS_PREFIX + "useSystemProperties";

    public static final boolean DEFAULT_USE_SYSTEM_PROPERTIES = false;

    /**
     * The name of retryHandler, supported values are “standard”, that obeys RFC-2616, regarding idempotent methods,
     * and “default” that considers requests w/o payload as idempotent.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #HTTP_RETRY_HANDLER_NAME_STANDARD}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_HTTP_RETRY_HANDLER_NAME = CONFIG_PROPS_PREFIX + "retryHandler.name";

    public static final String HTTP_RETRY_HANDLER_NAME_STANDARD = "standard";

    public static final String HTTP_RETRY_HANDLER_NAME_DEFAULT = "default";

    /**
     * Set to true if it is acceptable to retry non-idempotent requests, that have been sent.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED =
            CONFIG_PROPS_PREFIX + "retryHandler.requestSentEnabled";

    public static final boolean DEFAULT_HTTP_RETRY_HANDLER_REQUEST_SENT_ENABLED = false;

    /**
     * Comma-separated list of
     * <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#ciphersuites">Cipher
     * Suites</a> which are enabled for HTTPS connections.
     *
     * @since 2.0.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_CIPHER_SUITES = CONFIG_PROPS_PREFIX + "https.cipherSuites";

    /**
     * Comma-separated list of
     * <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#jssenames">Protocols
     * </a> which are enabled for HTTPS connections.
     *
     * @since 2.0.0
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_PROTOCOLS = CONFIG_PROPS_PREFIX + "https.protocols";

    /**
     * If enabled, Apache HttpClient will follow HTTP redirects.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_FOLLOW_REDIRECTS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.2
     */
    public static final String CONFIG_PROP_FOLLOW_REDIRECTS = CONFIG_PROPS_PREFIX + "followRedirects";

    public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /**
     * If enabled (default), operator-configured request headers ({@code aether.transport.http.headers}) are only
     * sent on requests targeting the repository origin (the scheme, host and port the repository URL denotes).
     * Apache HttpClient re-sends the original request headers on redirects, so without origin scoping a
     * cross-origin redirect replays the configured headers - which frequently carry credentials such as
     * {@code Authorization}, cookies or private token headers - to the redirect target host. Disable only when a
     * redirect target legitimately requires the configured headers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ORIGIN_SCOPED_HEADERS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.23
     */
    public static final String CONFIG_PROP_ORIGIN_SCOPED_HEADERS = CONFIG_PROPS_PREFIX + "originScopedHeaders";

    public static final boolean DEFAULT_ORIGIN_SCOPED_HEADERS = true;

    /**
     * The max redirect count to follow.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_REDIRECTS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.2
     */
    public static final String CONFIG_PROP_MAX_REDIRECTS = CONFIG_PROPS_PREFIX + "maxRedirects";

    public static final int DEFAULT_MAX_REDIRECTS = 5;

    /**
     * If enabled, Apache HttpClient will follow redirects that downgrade the protocol from https to http. Disabled
     * by default: such a downgrade strips transport encryption from artifact and checksum bytes and makes repository
     * credentials eligible for transmission over plaintext, so a downgrading redirect fails the transfer instead.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_FOLLOW_INSECURE_REDIRECTS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.23
     */
    public static final String CONFIG_PROP_FOLLOW_INSECURE_REDIRECTS = CONFIG_PROPS_PREFIX + "followInsecureRedirects";

    public static final boolean DEFAULT_FOLLOW_INSECURE_REDIRECTS = false;
}
