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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;

/**
 * Determines if updates of artifacts and metadata from remote repositories are needed.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface UpdateCheckManager {

    /**
     * Checks whether an artifact has to be updated from a remote repository.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void checkArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check);

    /**
     * Updates the timestamp for the artifact contained in the update check.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void touchArtifact(RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check);

    /**
     * Checks whether metadata has to be updated from a remote repository.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void checkMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check);

    /**
     * Updates the timestamp for the metadata contained in the update check.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void touchMetadata(RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check);
}
