/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
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
 */
public interface UpdateCheckManager
{

    /**
     * Returns the policy with the shorter update interval.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param policy1 A policy to compare, may be {@code null}.
     * @param policy2 A policy to compare, may be {@code null}.
     * @return The policy with the shorter update interval.
     */
    String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 );

    /**
     * Determines whether the specified modification timestamp satisfies the freshness constraint expressed by the given
     * update policy.
     * 
     * @param session The repository system session during which the check is made, must not be {@code null}.
     * @param lastModified The timestamp to check against the update policy.
     * @param policy The update policy, may be {@code null}.
     * @return {@code true} if the specified timestamp is older than acceptable by the update policy, {@code false}
     *         otherwise.
     */
    boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy );

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
