/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicyRequest;

/**
 * A resolution error policy that allows to control caching for artifacts and metadata at a global level.
 */
public final class SimpleResolutionErrorPolicy
    implements ResolutionErrorPolicy
{

    private final int artifactPolicy;

    private final int metadataPolicy;

    /**
     * Creates a new error policy with the specified behavior for both artifacts and metadata.
     * 
     * @param cacheNotFound {@code true} to enable caching of missing items, {@code false} to disable it.
     * @param cacheTransferErrors {@code true} to enable chaching of transfer errors, {@code false} to disable it.
     */
    public SimpleResolutionErrorPolicy( boolean cacheNotFound, boolean cacheTransferErrors )
    {
        this( ( cacheNotFound ? ResolutionErrorPolicy.CACHE_NOT_FOUND : 0 )
            | ( cacheTransferErrors ? ResolutionErrorPolicy.CACHE_TRANSFER_ERROR : 0 ) );
    }

    /**
     * Creates a new error policy with the specified bit mask for both artifacts and metadata.
     * 
     * @param policy The bit mask describing the policy for artifacts and metadata.
     */
    public SimpleResolutionErrorPolicy( int policy )
    {
        this( policy, policy );
    }

    /**
     * Creates a new error policy with the specified bit masks for artifacts and metadata.
     * 
     * @param artifactPolicy The bit mask describing the policy for artifacts.
     * @param metadataPolicy The bit mask describing the policy for metadata.
     */
    public SimpleResolutionErrorPolicy( int artifactPolicy, int metadataPolicy )
    {
        this.artifactPolicy = artifactPolicy;
        this.metadataPolicy = metadataPolicy;
    }

    public int getArtifactPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Artifact> request )
    {
        return artifactPolicy;
    }

    public int getMetadataPolicy( RepositorySystemSession session, ResolutionErrorPolicyRequest<Metadata> request )
    {
        return metadataPolicy;
    }

}
