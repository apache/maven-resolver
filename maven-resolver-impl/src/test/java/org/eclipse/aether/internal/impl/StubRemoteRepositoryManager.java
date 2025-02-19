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

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

import static java.util.Objects.requireNonNull;

public class StubRemoteRepositoryManager implements RemoteRepositoryManager {

    public StubRemoteRepositoryManager() {}

    @Override
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

    @Override
    public RepositoryPolicy getPolicy(
            RepositorySystemSession session, RemoteRepository repository, boolean releases, boolean snapshots) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        RepositoryPolicy policy = repository.getPolicy(snapshots);

        String checksums = session.getChecksumPolicy();
        if (checksums == null || checksums.isEmpty()) {
            checksums = policy.getChecksumPolicy();
        }
        String artifactUpdates = session.getArtifactUpdatePolicy();
        if (artifactUpdates == null || artifactUpdates.isEmpty()) {
            artifactUpdates = policy.getArtifactUpdatePolicy();
        }
        String metadataUpdates = session.getArtifactUpdatePolicy();
        if (metadataUpdates == null || metadataUpdates.isEmpty()) {
            metadataUpdates = policy.getMetadataUpdatePolicy();
        }

        policy = new RepositoryPolicy(policy.isEnabled(), artifactUpdates, metadataUpdates, checksums);

        return policy;
    }
}
