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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;

import static java.util.Objects.requireNonNull;

/* *
 */
class StubVersionResolver implements VersionResolver {

    public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        VersionResult result =
                new VersionResult(request).setVersion(request.getArtifact().getVersion());
        if (request.getRepositories().size() > 0) {
            result = result.setRepository(request.getRepositories().get(0));
        }
        return result;
    }
}
