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
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;

/**
 * Resolves metadata, that is gets a local filesystem path to their binary contents.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface MetadataResolver
{

    /**
     * Resolves the paths for a collection of metadata. Metadata will be downloaded to the local repository if
     * necessary, e.g. because it hasn't been cached yet or the cache is deemed outdated.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param requests The resolution requests, must not be {@code null}.
     * @return The resolution results (in request order), never {@code null}.
     * @see Metadata#getFile()
     * @see RepositorySystem#resolveMetadata(RepositorySystemSession, Collection)
     */
    List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                          Collection<? extends MetadataRequest> requests );

}
