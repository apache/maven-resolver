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
 */
public interface UpdateCheckManager
{

    /**
     * Checks whether an artifact has to be updated from a remote repository.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void checkArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check );

    /**
     * Updates the timestamp for the artifact contained in the update check.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void touchArtifact( RepositorySystemSession session, UpdateCheck<Artifact, ArtifactTransferException> check );

    /**
     * Checks whether metadata has to be updated from a remote repository.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void checkMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check );

    /**
     * Updates the timestamp for the metadata contained in the update check.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param check The update check request, must not be {@code null}.
     */
    void touchMetadata( RepositorySystemSession session, UpdateCheck<Metadata, MetadataTransferException> check );

}
