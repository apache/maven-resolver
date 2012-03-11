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
package org.eclipse.aether.resolution;

import org.eclipse.aether.artifact.Artifact;

/**
 * A query for the error policy for a given artifact's descriptor.
 * 
 * @see ArtifactDescriptorPolicy
 */
public final class ArtifactDescriptorPolicyRequest
{

    private Artifact artifact;

    private String context = "";

    /**
     * Creates an uninitialized request.
     */
    public ArtifactDescriptorPolicyRequest()
    {
        // enables default constructor
    }

    /**
     * Creates a request for the specified artifact.
     * 
     * @param artifact The artifact for whose descriptor to determine the error policy, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public ArtifactDescriptorPolicyRequest( Artifact artifact, String context )
    {
        setArtifact( artifact );
        setRequestContext( context );
    }

    /**
     * Gets the artifact for whose descriptor to determine the error policy.
     * 
     * @return The artifact for whose descriptor to determine the error policy or {@code null} if not set.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact for whose descriptor to determine the error policy.
     * 
     * @param artifact The artifact for whose descriptor to determine the error policy, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactDescriptorPolicyRequest setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the context in which this request is made.
     * 
     * @return The context, never {@code null}.
     */
    public String getRequestContext()
    {
        return context;
    }

    /**
     * Sets the context in which this request is made.
     * 
     * @param context The context, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactDescriptorPolicyRequest setRequestContext( String context )
    {
        this.context = ( context != null ) ? context : "";
        return this;
    }

    @Override
    public String toString()
    {
        return String.valueOf( getArtifact() );
    }

}
