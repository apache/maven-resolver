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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

/**
 * Provides information about an artifact that is relevant to transitive dependency resolution. Each artifact is expected
 * to have an accompanying <em>artifact descriptor</em> that among others lists the direct dependencies of the artifact.
 * 
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface ArtifactDescriptorReader
{

    /**
     * Gets information about an artifact like its direct dependencies and potential relocations. Implementations must
     * respect the {@link RepositorySystemSession#getArtifactDescriptorPolicy() artifact descriptor policy} of the
     * session when dealing with certain error cases.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The descriptor request, must not be {@code null}
     * @return The descriptor result, never {@code null}.
     * @throws ArtifactDescriptorException If the artifact descriptor could not be read.
     * @see RepositorySystem#readArtifactDescriptor(RepositorySystemSession, ArtifactDescriptorRequest)
     */
    ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session, ArtifactDescriptorRequest request )
        throws ArtifactDescriptorException;

}
