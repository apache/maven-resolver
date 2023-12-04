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
package org.eclipse.aether.spi.version;

import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.version.VersionScheme;

/**
 * Selects a version scheme from the installed version schemes.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @since 2.0.0
 */
public interface VersionSchemeSelector {
    /**
     * Tries to select a version scheme from the specified scheme name.
     *
     * @param schemeName The schemeName to select scheme for, must not be {@code null}.
     * @return The scheme selected, never {@code null}.
     * @throws IllegalArgumentException if asked scheme name is not supported.
     * @throws NullPointerException if passed in names is {@code null}.
     */
    VersionScheme selectVersionScheme(String schemeName);

    /**
     * Tries to select a version scheme from the specified scheme name.
     *
     * @param session The repository system session from which to configure the scheme, must not be {@code null}.
     * @return The scheme selected, never {@code null}.
     * @throws IllegalArgumentException If none of the installed schemes cannot be selected.
     * @throws NullPointerException if passed in session is {@code null}.
     */
    VersionScheme selectVersionScheme(RepositorySystemSession session);

    /**
     * Returns immutable map of all supported version schemes (maps scheme name to scheme instance). This represents
     * ALL the schemes supported by Resolver, either provided out of the box, or extension installed.
     */
    Map<String, VersionScheme> getVersionSchemes();
}
