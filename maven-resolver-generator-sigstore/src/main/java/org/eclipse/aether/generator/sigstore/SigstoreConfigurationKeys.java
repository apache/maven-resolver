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
package org.eclipse.aether.generator.sigstore;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for Sigstore Signer.
 *
 * @since 2.0.2
 */
public final class SigstoreConfigurationKeys {
    private SigstoreConfigurationKeys() {}

    static final String NAME = "sigstore";

    static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_GENERATOR + NAME + ".";

    /**
     * Whether Sigstore signer is enabled.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = CONFIG_PROPS_PREFIX + "enabled";

    public static final boolean DEFAULT_ENABLED = false;

    /**
     * Whether Sigstore should use public staging {@code sigstage.dev} instead of public default {@code sigstore.dev}.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_PUBLIC_STAGING}
     */
    public static final String CONFIG_PROP_PUBLIC_STAGING = CONFIG_PROPS_PREFIX + "publicStaging";

    public static final boolean DEFAULT_PUBLIC_STAGING = false;
}
