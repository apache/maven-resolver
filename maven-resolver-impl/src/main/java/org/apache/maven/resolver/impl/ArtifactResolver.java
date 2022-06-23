package org.apache.maven.resolver.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.List;

import org.apache.maven.resolver.RepositorySystem;
import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.resolution.ArtifactRequest;
import org.apache.maven.resolver.resolution.ArtifactResolutionException;
import org.apache.maven.resolver.resolution.ArtifactResult;

/**
 * Resolves artifacts, that is gets a local filesystem path to their binary contents.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface ArtifactResolver
{

    /**
     * Resolves the path for an artifact. The artifact will be downloaded to the local repository if necessary. An
     * artifact that is already resolved will be skipped and is not re-resolved. Note that this method assumes that any
     * relocations have already been processed and the artifact coordinates are used as-is.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The resolution request, must not be {@code null}.
     * @return The resolution result, never {@code null}.
     * @throws ArtifactResolutionException If the artifact could not be resolved.
     * @see Artifact#getFile()
     * @see RepositorySystem#resolveArtifact(RepositorySystemSession, ArtifactRequest)
     */
    ArtifactResult resolveArtifact( RepositorySystemSession session, ArtifactRequest request )
        throws ArtifactResolutionException;

    /**
     * Resolves the paths for a collection of artifacts. Artifacts will be downloaded to the local repository if
     * necessary. Artifacts that are already resolved will be skipped and are not re-resolved. Note that this method
     * assumes that any relocations have already been processed and the artifact coordinates are used as-is.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @throws ArtifactResolutionException If any artifact could not be resolved.
     * @see Artifact#getFile()
     * @see RepositorySystem#resolveArtifacts(RepositorySystemSession, Collection)
     */
    List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                           Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException;

}
