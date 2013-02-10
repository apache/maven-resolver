/*******************************************************************************
 * Copyright (c) 2012, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.resolution;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * Controls the caching of resolution errors for artifacts/metadata from remote repositories. If caching is enabled for
 * a given resource, a marker will be set (usually somewhere in the local repository) to suppress repeated resolution
 * attempts for the broken resource, thereby avoiding expensive but useless network IO. The error marker is considered
 * stale once the repository's update policy has expired at which point a future resolution attempt will be allowed.
 * Error caching considers the current network settings such that fixes to the configuration like authentication or
 * proxy automatically trigger revalidation with the remote side regardless of the time elapsed since the previous
 * resolution error.
 * 
 * @see RepositorySystemSession#getResolutionErrorPolicy()
 */
public interface ResolutionErrorPolicy
{

    /**
     * Bit mask indicating that resolution errors should not be cached in the local repository. This forces the system
     * to always query the remote repository for locally missing artifacts/metadata.
     */
    int CACHE_DISABLED = 0x00;

    /**
     * Bit flag indicating whether missing artifacts/metadata should be cached in the local repository. If caching is
     * enabled, resolution will not be reattempted until the update policy for the affected resource has expired.
     */
    int CACHE_NOT_FOUND = 0x01;

    /**
     * Bit flag indicating whether connectivity/transfer errors (e.g. unreachable host, bad authentication) should be
     * cached in the local repository. If caching is enabled, resolution will not be reattempted until the update policy
     * for the affected resource has expired.
     */
    int CACHE_TRANSFER_ERROR = 0x02;

    /**
     * Bit mask indicating that all resolution errors should be cached in the local repository.
     */
    int CACHE_ALL = CACHE_NOT_FOUND | CACHE_TRANSFER_ERROR;

    /**
     * Gets the error policy for an artifact.
     * 
     * @param session The repository session during which the policy is determined, must not be {@code null}.
     * @param request The policy request holding further details, must not be {@code null}.
     * @return The bit mask describing the desired error policy.
     */
    int getArtifactPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Artifact> request );

    /**
     * Gets the error policy for some metadata.
     * 
     * @param session The repository session during which the policy is determined, must not be {@code null}.
     * @param request The policy request holding further details, must not be {@code null}.
     * @return The bit mask describing the desired error policy.
     */
    int getMetadataPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Metadata> request );

}
