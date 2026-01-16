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
package org.eclipse.aether.transport.ipfs;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Configuration for IPFS Transport.
 *
 * @since 1.9.26
 */
public final class IpfsTransporterConfigurationKeys {
    private IpfsTransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX = "aether.transport." + IpfsTransporterFactory.NAME + ".";

    /**
     * Multiaddress of node to connect to, by default expects local node.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_MULTIADDR}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_MULTIADDR = CONFIG_PROPS_PREFIX + "multiaddr";

    public static final String DEFAULT_MULTIADDR = "/ip4/127.0.0.1/tcp/5001";

    /**
     * The prefix to use before namespace.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_FILES_PREFIX}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_FILES_PREFIX = CONFIG_PROPS_PREFIX + "filesPrefix";

    public static final String DEFAULT_FILES_PREFIX = "publish";

    /**
     * Whether to refresh IPNS record before deployment.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_REFRESH_IPNS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_REFRESH_IPNS = CONFIG_PROPS_PREFIX + "refreshIpns";

    public static final boolean DEFAULT_REFRESH_IPNS = true;

    /**
     * Whether to publish IPNS record for deployment. In Resolver 1.9.x this is tricky, as it has no notion of
     * "session end", hence, this option is usable (will behave as expected) only if transport is used in deployment
     * with "deploy at end" feature. Because of that, default is {@code false}.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_PUBLISH_IPNS}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_PUBLISH_IPNS = CONFIG_PROPS_PREFIX + "publishIpns";

    public static final boolean DEFAULT_PUBLISH_IPNS = false;

    /**
     * The name of the key to publish IPNS record. It has to exist in the current node, or can be created. The default
     * value uses same value as namespace value is.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_PUBLISH_IPNS_KEY_NAME = CONFIG_PROPS_PREFIX + "publishIpnsKeyName";

    /**
     * Whether to create key if there is no key with given name. If {@code false} publishing will fail if no key found.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_PUBLISH_IPNS_KEY_CREATE}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_PUBLISH_IPNS_KEY_CREATE = CONFIG_PROPS_PREFIX + "publishIpnsKeyCreate";

    public static final boolean DEFAULT_PUBLISH_IPNS_KEY_CREATE = true;

    /**
     * By default, we assume that namespace is reverse domain, and it is <em>real prefix</em> of artifacts within
     * given namespace (hence, it can be used in RRF as prefix source).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_NAMESPACE_IS_PREFIX}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_NAMESPACE_IS_PREFIX = CONFIG_PROPS_PREFIX + "namespaceIsPrefix";

    public static final boolean DEFAULT_NAMESPACE_IS_PREFIX = true;
}
