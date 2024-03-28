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
package org.eclipse.aether.generator.gnupg;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for GPG Signer.
 *
 * @since 2.0.0
 */
public final class GnupgConfigurationKeys {
    private GnupgConfigurationKeys() {}

    static final String NAME = "gpg";

    static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_GENERATOR + NAME + ".";

    /**
     * Whether GnuPG signer is enabled.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ENABLED}
     */
    public static final String CONFIG_PROP_ENABLED = CONFIG_PROPS_PREFIX + "enabled";

    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The PGP Key fingerprint as hex string (40 characters long), optional. If not set, first secret key found will
     * be used.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     */
    public static final String CONFIG_PROP_KEY_FINGERPRINT = CONFIG_PROPS_PREFIX + "keyFingerprint";

    /**
     * The path to the OpenPGP transferable secret key file. If relative, is resolved from local repository root.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_KEY_FILE_PATH}
     */
    public static final String CONFIG_PROP_KEY_FILE_PATH = CONFIG_PROPS_PREFIX + "keyFilePath";

    public static final String DEFAULT_KEY_FILE_PATH = "maven-signing-key.key";

    /**
     * Whether GnuPG agent should be used.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_USE_AGENT}
     */
    public static final String CONFIG_PROP_USE_AGENT = CONFIG_PROPS_PREFIX + "useAgent";

    public static final boolean DEFAULT_USE_AGENT = true;

    /**
     * The GnuPG agent socket(s) to try. Comma separated list of socket paths. If relative, will be resolved from
     * user home directory.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_AGENT_SOCKET_LOCATIONS}
     */
    public static final String CONFIG_PROP_AGENT_SOCKET_LOCATIONS = CONFIG_PROPS_PREFIX + "agentSocketLocations";

    public static final String DEFAULT_AGENT_SOCKET_LOCATIONS = ".gnupg/S.gpg-agent";

    /**
     * Env variable name to pass in key pass.
     */
    public static final String RESOLVER_GPG_KEY_PASS = "RESOLVER_GPG_KEY_PASS";

    /**
     * Env variable name to pass in key material.
     */
    public static final String RESOLVER_GPG_KEY = "RESOLVER_GPG_KEY";

    /**
     * Env variable name to pass in key fingerprint (hex encoded, 40 characters long).
     */
    public static final String RESOLVER_GPG_KEY_FINGERPRINT = "RESOLVER_GPG_KEY_FINGERPRINT";
}
