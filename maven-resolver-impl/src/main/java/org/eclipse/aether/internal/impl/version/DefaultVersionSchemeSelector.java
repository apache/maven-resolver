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
package org.eclipse.aether.internal.impl.version;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.Map;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.version.VersionSchemeSelector;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.version.VersionScheme;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation.
 *
 * @since 2.0.0
 */
@Singleton
@Named
public class DefaultVersionSchemeSelector implements VersionSchemeSelector {
    static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_AETHER + "versionScheme.";

    /**
     * The name of the version scheme to be used in session.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_VERSION_SCHEME_NAME}
     */
    public static final String CONFIG_PROP_VERSION_SCHEME_NAME = CONFIG_PROPS_PREFIX + "name";

    public static final String DEFAULT_VERSION_SCHEME_NAME = GenericVersionSchemeProvider.NAME;

    private final Map<String, VersionScheme> versionSchemes;

    @Inject
    public DefaultVersionSchemeSelector(Map<String, VersionScheme> versionSchemes) {
        this.versionSchemes = requireNonNull(versionSchemes);
    }

    @Override
    public VersionScheme selectVersionScheme(String schemeName) {
        requireNonNull(schemeName, "null schemeName");
        VersionScheme versionScheme = versionSchemes.get(schemeName);
        if (versionScheme == null) {
            throw new IllegalArgumentException("scheme not supported");
        }
        return versionScheme;
    }

    @Override
    public VersionScheme selectVersionScheme(RepositorySystemSession session) {
        return selectVersionScheme(
                ConfigUtils.getString(session, DEFAULT_VERSION_SCHEME_NAME, CONFIG_PROP_VERSION_SCHEME_NAME));
    }

    @Override
    public Map<String, VersionScheme> getVersionSchemes() {
        return Collections.unmodifiableMap(versionSchemes);
    }
}
