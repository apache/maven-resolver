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
package org.eclipse.aether.transport.minio;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transport.minio.internal.FixedBucketObjectNameMapperFactory;

/**
 * Configuration for MinIO Transport.
 *
 * @since 2.0.2
 */
public final class MinioTransporterConfigurationKeys {
    private MinioTransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + MinioTransporterFactory.NAME + ".";

    /**
     * Object name mapper to use.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_OBJECT_NAME_MAPPER}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_OBJECT_NAME_MAPPER = CONFIG_PROPS_PREFIX + "objectNameMapper";

    public static final String DEFAULT_OBJECT_NAME_MAPPER = FixedBucketObjectNameMapperFactory.NAME;

    /**
     * The fixed bucket name to use.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_FIXED_BUCKET_NAME}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_FIXED_BUCKET_NAME = CONFIG_PROPS_PREFIX + "fixedBucketName";

    public static final String DEFAULT_FIXED_BUCKET_NAME = "maven";
}
