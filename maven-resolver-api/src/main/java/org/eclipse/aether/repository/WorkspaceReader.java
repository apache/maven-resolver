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
package org.eclipse.aether.repository;

import java.io.File;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;

/**
 * Manages a repository backed by the IDE workspace, a build session or a similar ad-hoc collection of artifacts.
 *
 * @see org.eclipse.aether.RepositorySystemSession#getWorkspaceReader()
 */
public interface WorkspaceReader {

    /**
     * Gets a description of the workspace repository.
     *
     * @return The repository description, never {@code null}.
     */
    WorkspaceRepository getRepository();

    /**
     * Locates the specified artifact.
     *
     * @param artifact The artifact to locate, must not be {@code null}.
     * @return The path to the artifact or {@code null} if the artifact is not available.
     */
    File findArtifact(Artifact artifact);

    /**
     * Determines all available versions of the specified artifact.
     *
     * @param artifact The artifact whose versions should be listed, must not be {@code null}.
     * @return The available versions of the artifact, must not be {@code null}.
     */
    List<String> findVersions(Artifact artifact);
}
