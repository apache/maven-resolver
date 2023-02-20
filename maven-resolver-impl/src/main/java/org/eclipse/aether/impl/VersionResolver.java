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
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;

/**
 * Evaluates artifact meta/pseudo versions.
 *
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface VersionResolver {

    /**
     * Resolves an artifact's meta version (if any) to a concrete version. For example, resolves "1.0-SNAPSHOT" to
     * "1.0-20090208.132618-23" or "RELEASE"/"LATEST" to "2.0".
     *
     * @param session The repository session, must not be {@code null}.
     * @param request The version request, must not be {@code null}
     * @return The version result, never {@code null}.
     * @throws VersionResolutionException If the metaversion could not be resolved.
     * @see RepositorySystem#resolveVersion(RepositorySystemSession, VersionRequest)
     */
    VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
            throws VersionResolutionException;
}
