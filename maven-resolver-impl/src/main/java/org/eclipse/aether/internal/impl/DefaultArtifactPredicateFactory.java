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
package org.eclipse.aether.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.artifact.ArtifactPredicate;
import org.eclipse.aether.spi.artifact.ArtifactPredicateFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.util.ConfigUtils;

@Singleton
@Named
public final class DefaultArtifactPredicateFactory implements ArtifactPredicateFactory {
    private static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_CHECKSUMS;

    /**
     * Comma-separated list of extensions with leading dot (example ".asc") that should have checksums omitted.
     * These are applied to sub-artifacts only. Note: to achieve 1.7.x aether.checksums.forSignature=true behaviour,
     * pass empty string as value for this property.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS}
     * @configurationRepoIdSuffix No
     */
    public static final String CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS =
            CONFIG_PROPS_PREFIX + "omitChecksumsForExtensions";

    public static final String DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS = ".asc,.sigstore";

    private final ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector;

    @Inject
    public DefaultArtifactPredicateFactory(ChecksumAlgorithmFactorySelector checksumAlgorithmFactorySelector) {
        this.checksumAlgorithmFactorySelector = checksumAlgorithmFactorySelector;
    }

    @Override
    public ArtifactPredicate newInstance(RepositorySystemSession session) {
        // ensure uniqueness of (potentially user set) extension list
        Set<String> omitChecksumsForExtensions = Arrays.stream(ConfigUtils.getString(
                                session,
                                DEFAULT_OMIT_CHECKSUMS_FOR_EXTENSIONS,
                                CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS)
                        .split(","))
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toSet());

        // validation: enforce that all strings in this set are having leading dot
        if (omitChecksumsForExtensions.stream().anyMatch(s -> !s.startsWith("."))) {
            throw new IllegalArgumentException(String.format(
                    "The configuration %s contains illegal values: %s (all entries must start with '.' (dot))",
                    CONFIG_PROP_OMIT_CHECKSUMS_FOR_EXTENSIONS, omitChecksumsForExtensions));
        }
        return new DefaultArtifactPredicate(checksumAlgorithmFactorySelector, omitChecksumsForExtensions);
    }
}
