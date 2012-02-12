/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.repository;

import java.io.File;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;

/**
 * Manages a repository backed by the IDE workspace, a build session or a similar ad-hoc collection of artifacts.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getWorkspaceReader()
 */
public interface WorkspaceReader
{

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
    File findArtifact( Artifact artifact );

    /**
     * Determines all available versions of the specified artifact.
     * 
     * @param artifact The artifact whose versions should be listed, must not be {@code null}.
     * @return The available versions of the artifact, must not be {@code null}.
     */
    List<String> findVersions( Artifact artifact );

}
