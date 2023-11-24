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

import java.net.http.HttpClient;

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
     */
    public static final String CONFIG_PROP_HTTP_VERSION = CONFIG_PROPS_PREFIX + "httpVersion";

    public static final String DEFAULT_HTTP_VERSION = HttpClient.Version.HTTP_2.name();
}
