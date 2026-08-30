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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * JDK Transport configuration keys.
 *
 * @since 2.0.0
 */
public final class JdkTransporterConfigurationKeys {
    private JdkTransporterConfigurationKeys() {}

    private static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + JdkTransporterFactory.NAME + ".";

    /**
     * Use string representation of HttpClient version enum "HTTP_2" or "HTTP_1_1" to set default HTTP protocol to use.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_HTTP_VERSION}
     * @configurationRepoIdSuffix Yes
     * @deprecated Use {@link ConfigurationProperties#HTTP_VERSION} instead. This property is kept for backward compatibility and will be removed in future versions.
     */
    @Deprecated
    public static final String CONFIG_PROP_HTTP_VERSION = CONFIG_PROPS_PREFIX + "httpVersion";

    public static final String DEFAULT_HTTP_VERSION = "HTTP_1_1";

    /**
     * The hard limit of maximum concurrent requests JDK transport can do. This is a workaround for the fact, that in
     * HTTP/2 mode, JDK HttpClient initializes this value to Integer.MAX_VALUE (!) and lowers it on first response
     * from the remote server (but it may be too late). See JDK bug
     * <a href="https://bugs.openjdk.org/browse/JDK-8225647">JDK-8225647</a> for details.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_CONCURRENT_REQUESTS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_MAX_CONCURRENT_REQUESTS = CONFIG_PROPS_PREFIX + "maxConcurrentRequests";

    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 100;

    /**
     * If enabled, restores the legacy behavior where the transporter's {@link java.net.Authenticator} handed out
     * the repository (or proxy) credentials to any host that issued an authentication challenge - including hosts
     * reached by following a redirect off the repository. When disabled (the default), credentials are only
     * returned when the challenging origin (protocol, host and port) matches the repository base URI, or, for
     * proxy challenges, the configured proxy address.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_UNSCOPED_AUTHENTICATION}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.22
     */
    public static final String CONFIG_PROP_UNSCOPED_AUTHENTICATION = CONFIG_PROPS_PREFIX + "unscopedAuthentication";

    public static final boolean DEFAULT_UNSCOPED_AUTHENTICATION = false;

    /**
     * If enabled (default), operator-configured request headers ({@code aether.transport.http.headers}) and
     * preemptively applied {@code Authorization} are only sent on requests targeting the repository origin (the
     * scheme, host and port the repository URL denotes). The JDK {@link java.net.http.HttpClient} re-sends all
     * user-set headers on every redirect hop it follows - including hops that leave the repository origin - so
     * with this enabled the transporter configures the client with {@code Redirect.NEVER} and follows redirects
     * itself, dropping those headers on any hop that leaves the origin. Disable only when a redirect target
     * legitimately requires the configured headers; disabling restores the JDK client's own redirect handling
     * ({@code Redirect.NORMAL}) and the legacy header replay.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ORIGIN_SCOPED_HEADERS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.22
     */
    public static final String CONFIG_PROP_ORIGIN_SCOPED_HEADERS = CONFIG_PROPS_PREFIX + "originScopedHeaders";

    public static final boolean DEFAULT_ORIGIN_SCOPED_HEADERS = true;
}
