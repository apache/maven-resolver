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
package org.eclipse.aether.transport.url;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for URL Transport.
 *
 * @since 2.0.21
 */
public final class UrlTransporterConfigurationKeys {
    private UrlTransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + UrlTransporterFactory.NAME + ".";

    /**
     * The redirect mode of the transport. Accepted values are {@code NONE}, when any redirect response from server
     * result in transport error, {@code SAME_AUTHORITY} when transport follow redirects only happening within same
     * URI authority (same server), and {@code ANY} when redirects are followed everywhere.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_REDIRECT_MODE}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_REDIRECT_MODE = CONFIG_PROPS_PREFIX + "redirectMode";

    public static final String DEFAULT_REDIRECT_MODE = "ANY";

    /**
     * Maximum count of redirects that transport follows within one transaction.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Integer}
     * @configurationDefaultValue {@link #DEFAULT_MAX_REDIRECT_COUNT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_MAX_REDIRECT_COUNT = CONFIG_PROPS_PREFIX + "maxRedirectCount";

    public static final int DEFAULT_MAX_REDIRECT_COUNT = 5;

    /**
     * Whether protocol downgrade (HTTPS -> HTTP) is allowed during redirect following.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_REDIRECT_ALLOW_DOWNGRADE}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_REDIRECT_ALLOW_DOWNGRADE = CONFIG_PROPS_PREFIX + "redirectAllowDowngrade";

    public static final boolean DEFAULT_REDIRECT_ALLOW_DOWNGRADE = false;
}
