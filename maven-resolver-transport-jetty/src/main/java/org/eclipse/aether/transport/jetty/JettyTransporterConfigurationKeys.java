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
}
