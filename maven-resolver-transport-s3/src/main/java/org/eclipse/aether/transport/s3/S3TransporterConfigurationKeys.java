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
package org.eclipse.aether.transport.s3;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transport.s3.internal.FixedBucketObjectNameMapperFactory;

/**
 * Configuration for the S3 transport.
 *
 * @since 2.0.26
 */
public final class S3TransporterConfigurationKeys {
    private S3TransporterConfigurationKeys() {}

    static final String CONFIG_PROPS_PREFIX =
            ConfigurationProperties.PREFIX_TRANSPORT + S3TransporterFactory.NAME + ".";

    /**
     * The prefix of the MinIO transport's keys. The keys below that have a MinIO counterpart are also read under this
     * prefix, so that a repository configured for the MinIO transport works with this one unchanged.
     */
    static final String LEGACY_CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_TRANSPORT + "minio.";

    /**
     * Object name mapper to use. Also read under the MinIO transport's key, {@code aether.transport.minio.objectNameMapper}.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_OBJECT_NAME_MAPPER}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_OBJECT_NAME_MAPPER = CONFIG_PROPS_PREFIX + "objectNameMapper";

    static final String LEGACY_CONFIG_PROP_OBJECT_NAME_MAPPER = LEGACY_CONFIG_PROPS_PREFIX + "objectNameMapper";

    public static final String DEFAULT_OBJECT_NAME_MAPPER = FixedBucketObjectNameMapperFactory.NAME;

    /**
     * The fixed bucket name to use. Also read under the MinIO transport's key, {@code aether.transport.minio.fixedBucketName}.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_FIXED_BUCKET_NAME}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_FIXED_BUCKET_NAME = CONFIG_PROPS_PREFIX + "fixedBucketName";

    public static final String LEGACY_CONFIG_PROP_FIXED_BUCKET_NAME = LEGACY_CONFIG_PROPS_PREFIX + "fixedBucketName";

    public static final String DEFAULT_FIXED_BUCKET_NAME = "maven";

    /**
     * The AWS region the requests are signed for. S3-compatible stores accept any value, but Signature Version 4
     * needs one. When unset, the SDK's default region provider chain is consulted (environment, system properties,
     * profile), and {@link #DEFAULT_REGION} is used if that chain yields nothing.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link String}
     * @configurationDefaultValue {@link #DEFAULT_REGION}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_REGION = CONFIG_PROPS_PREFIX + "region";

    public static final String DEFAULT_REGION = "us-east-1";

    /**
     * Whether plaintext HTTP object storage endpoints (repository URLs in {@code s3+http} form) are allowed.
     * Plaintext endpoints offer no transport integrity (artifacts and their checksum objects travel over the same
     * tamperable channel) and, because the repository protocol string is not {@code http}, such URLs are not matched
     * by {@code external:http:*} mirror blocking. They are therefore refused unless this property is explicitly set
     * to {@code true}. Also read under the MinIO transport's key, {@code aether.transport.minio.allowInsecureProtocol}.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link Boolean}
     * @configurationDefaultValue {@link #DEFAULT_ALLOW_INSECURE_PROTOCOL}
     * @configurationRepoIdSuffix Yes
     */
    public static final String CONFIG_PROP_ALLOW_INSECURE_PROTOCOL = CONFIG_PROPS_PREFIX + "allowInsecureProtocol";

    static final String LEGACY_CONFIG_PROP_ALLOW_INSECURE_PROTOCOL =
            LEGACY_CONFIG_PROPS_PREFIX + "allowInsecureProtocol";

    public static final boolean DEFAULT_ALLOW_INSECURE_PROTOCOL = false;
}
