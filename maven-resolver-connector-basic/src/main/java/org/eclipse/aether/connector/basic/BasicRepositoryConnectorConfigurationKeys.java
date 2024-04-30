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
package org.eclipse.aether.connector.basic;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * The configuration keys for {@link BasicRepositoryConnector}.
 *
 * @since 2.0.0
 */
public final class BasicRepositoryConnectorConfigurationKeys {
    private BasicRepositoryConnectorConfigurationKeys() {}

    /**
     * The prefix for configuration properties.
     */
    public static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_CONNECTOR + BasicRepositoryConnectorFactory.NAME + ".";

    /**
     * Flag indicating whether checksums which are retrieved during checksum validation should be persisted in the
     * local repository next to the file they provide the checksum for.
     *
     * @since 0.9.0.M4
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_PERSISTED_CHECKSUMS}
     * @configurationRepoIdSuffix No
     */
    public static final String CONFIG_PROP_PERSISTED_CHECKSUMS = CONFIG_PROPS_PREFIX + "persistedChecksums";

    public static final boolean DEFAULT_PERSISTED_CHECKSUMS = true;

    /**
     * Number of threads in basic connector for uploading/downloading.
     *
     * @since 0.9.0.M4
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue {@link #DEFAULT_THREADS}
     * @configurationRepoIdSuffix No
     */
    public static final String CONFIG_PROP_THREADS = CONFIG_PROPS_PREFIX + "threads";

    public static final int DEFAULT_THREADS = 5;

    /**
     * Enables or disables parallel PUT processing (parallel deploys) on basic connector globally or per remote
     * repository. When disabled, connector behaves exactly as in Maven 3.8.x did: GETs are parallel while PUTs
     * are sequential.
     *
     * @since 1.9.5
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_PARALLEL_PUT}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_PARALLEL_PUT = CONFIG_PROPS_PREFIX + "parallelPut";

    public static final boolean DEFAULT_PARALLEL_PUT = true;

    /**
     * Flag indicating that instead of comparing the external checksum fetched from the remote repo with the
     * calculated one, it should try to extract the reference checksum from the actual artifact response headers
     * This only works for HTTP transports.
     *
     * @since 0.9.0.M3
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_SMART_CHECKSUMS}
     * @configurationRepoIdSuffix No
     */
    public static final String CONFIG_PROP_SMART_CHECKSUMS = CONFIG_PROPS_PREFIX + "smartChecksums";

    public static final boolean DEFAULT_SMART_CHECKSUMS = true;
}
