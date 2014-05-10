/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

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
