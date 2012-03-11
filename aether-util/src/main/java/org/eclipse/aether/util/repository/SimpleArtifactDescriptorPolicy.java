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
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicyRequest;

/**
 * An artifact descriptor error policy that allows to control error handling at a global level.
 */
public final class SimpleArtifactDescriptorPolicy
    implements ArtifactDescriptorPolicy
{

    private final int policy;

    /**
     * Creates a new error policy with the specified behavior.
     * 
     * @param ignoreMissing {@code true} to ignore missing descriptors, {@code false} to fail resolution.
     * @param ignoreInvalid {@code true} to ignore invalid descriptors, {@code false} to fail resolution.
     */
    public SimpleArtifactDescriptorPolicy( boolean ignoreMissing, boolean ignoreInvalid )
    {
        this( ( ignoreMissing ? IGNORE_MISSING : 0 ) | ( ignoreInvalid ? IGNORE_INVALID : 0 ) );
    }

    /**
     * Creates a new error policy with the specified bit mask.
     * 
     * @param policy The bit mask describing the policy.
     */
    public SimpleArtifactDescriptorPolicy( int policy )
    {
        this.policy = policy;
    }

    public int getPolicy( RepositorySystemSession session, ArtifactDescriptorPolicyRequest request )
    {
        return policy;
    }

}
