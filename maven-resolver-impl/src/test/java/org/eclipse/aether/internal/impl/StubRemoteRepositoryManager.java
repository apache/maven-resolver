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

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

public class StubRemoteRepositoryManager implements RemoteRepositoryManager {

    public StubRemoteRepositoryManager() {}

    public List<RemoteRepository> aggregateRepositories(
            RepositorySystemSession session,
            List<RemoteRepository> dominantRepositories,
            List<RemoteRepository> recessiveRepositories,
            boolean recessiveIsRaw) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(dominantRepositories, "dominantRepositories cannot be null");
        requireNonNull(recessiveRepositories, "recessiveRepositories cannot be null");
        return dominantRepositories;
    }

    public RepositoryPolicy getPolicy(
            RepositorySystemSession session, RemoteRepository repository, boolean releases, boolean snapshots) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        RepositoryPolicy policy = repository.getPolicy(snapshots);

        String checksums = session.getChecksumPolicy();
        if (StringUtils.isEmpty(checksums)) {
            checksums = policy.getChecksumPolicy();
        }
        String updates = session.getUpdatePolicy();
        if (StringUtils.isEmpty(updates)) {
            updates = policy.getUpdatePolicy();
        }

        policy = new RepositoryPolicy(policy.isEnabled(), updates, checksums);

        return policy;
    }
}
