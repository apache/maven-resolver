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
package org.eclipse.aether.transport.wagon;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;

/**
 * A transporter using Maven Wagon.
 */
public final class WagonTransporterConfigurationKeys {
    private WagonTransporterConfigurationKeys() {}

    private static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + WagonTransporterFactory.NAME + ".";

    /**
     * The configuration to use for the Wagon provider.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Object}
     */
    public static final String CONFIG_PROP_CONFIG = CONFIG_PROPS_PREFIX + "config";

    /**
     * Octal numerical notation of permissions to set for newly created files. Only considered by certain Wagon
     * providers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_FILE_MODE = CONFIG_PROPS_PREFIX + "perms.fileMode";

    /**
     * Octal numerical notation of permissions to set for newly created directories. Only considered by certain
     * Wagon providers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_DIR_MODE = CONFIG_PROPS_PREFIX + "perms.dirMode";

    /**
     * Group which should own newly created directories/files. Only considered by certain Wagon providers.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     */
    public static final String CONFIG_PROP_GROUP = CONFIG_PROPS_PREFIX + "perms.group";
}
