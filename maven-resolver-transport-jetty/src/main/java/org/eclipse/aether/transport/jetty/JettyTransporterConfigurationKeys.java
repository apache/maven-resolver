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

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for Jetty Transport.
 *
 * @since 2.0.1
 */
public final class JettyTransporterConfigurationKeys {
    private JettyTransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + JettyTransporterFactory.NAME + ".";

    /**
     * If enabled, Jetty client will follow HTTP redirects.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_FOLLOW_REDIRECTS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_FOLLOW_REDIRECTS = CONFIG_PROPS_PREFIX + "followRedirects";

    public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /**
     * The max redirect count to follow.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_REDIRECTS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_MAX_REDIRECTS = CONFIG_PROPS_PREFIX + "maxRedirects";

    public static final int DEFAULT_MAX_REDIRECTS = 5;

    /**
     * If enabled, Jetty client will follow redirects that downgrade the protocol from https to http. Disabled by
     * default: such a downgrade strips transport encryption from artifact and checksum bytes and makes repository
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

    /**
     * If enabled (default), operator-configured request headers ({@code aether.transport.http.headers}) and
     * preemptively applied {@code Authorization} are only sent on requests targeting the repository origin (the
     * scheme, host and port the repository URL denotes). Jetty's redirector copies the request headers onto every
     * redirect hop it follows, so without origin scoping a cross-origin redirect replays the configured headers -
     * which frequently carry credentials such as {@code Authorization} or private token headers - to the redirect
     * target host. Disable only when a redirect target legitimately requires the configured headers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ORIGIN_SCOPED_HEADERS}
     * @configurationRepoIdSuffix Yes
     * @since 2.0.23
     */
    public static final String CONFIG_PROP_ORIGIN_SCOPED_HEADERS = CONFIG_PROPS_PREFIX + "originScopedHeaders";

    public static final boolean DEFAULT_ORIGIN_SCOPED_HEADERS = true;
}
