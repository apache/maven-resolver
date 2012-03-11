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

import org.eclipse.aether.RepositorySystemSession;

/**
 * Controls the handling of errors related to reading an artifact descriptor.
 * 
 * @see RepositorySystemSession#getArtifactDescriptorPolicy()
 */
public interface ArtifactDescriptorPolicy
{

    /**
     * Bit mask indicating that errors while reading the artifact descriptor should not be tolerated.
     */
    public static final int STRICT = 0x00;

    /**
     * Bit flag indicating that missing artifact descriptors should be silently ignored.
     */
    public static final int IGNORE_MISSING = 0x01;

    /**
     * Bit flag indicating that existent but invalid artifact descriptors should be silently ignored.
     */
    public static final int IGNORE_INVALID = 0x02;

    /**
     * Bit mask indicating that all errors should be silently ignored.
     */
    public static final int IGNORE_ERRORS = IGNORE_MISSING | IGNORE_INVALID;

    /**
     * Gets the error policy for an artifact's descriptor.
     * 
     * @param session The repository session during which the policy is determined, must not be {@code null}.
     * @param request The policy request holding further details, must not be {@code null}.
     * @return The bit mask describing the desired error policy.
     */
    int getPolicy( RepositorySystemSession session, ArtifactDescriptorPolicyRequest request );

}
