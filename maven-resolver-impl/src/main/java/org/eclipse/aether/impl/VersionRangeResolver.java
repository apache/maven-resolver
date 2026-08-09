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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 * Parses and evaluates version ranges encountered in dependency declarations.
 *
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface VersionRangeResolver {

    /**
     * Expands a version range to a list of matching versions, in ascending order. For example, resolves "[3.8,4.0)" to
     * "3.8", "3.8.1", "3.8.2". The returned list of versions is only dependent on the configured repositories and their
     * contents, the list is not processed by the {@link RepositorySystemSession#getVersionFilter() session's version
     * filter}.
     * <p>
     * The supplied request may also refer to a single concrete version rather than a version range. In this case
     * though, the result contains simply the (parsed) input version, regardless of the repositories and their contents.
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The version range request, must not be {@code null}.
     * @return The version range result, never {@code null}.
     * @throws VersionRangeResolutionException If the requested range could not be parsed. Note that an empty range does
     *             not raise an exception.
     * @see RepositorySystem#resolveVersionRange(RepositorySystemSession, VersionRangeRequest)
     */
    VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
            throws VersionRangeResolutionException;
}
