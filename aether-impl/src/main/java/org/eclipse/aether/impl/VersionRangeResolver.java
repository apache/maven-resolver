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
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 * Parses and evaluates version ranges encountered in dependency declarations.
 * 
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface VersionRangeResolver
{

    /**
     * Expands a version range to a list of matching versions, in ascending order. For example, resolves "[3.8,4.0)" to
     * "3.8", "3.8.1", "3.8.2". The returned list of versions is only dependent on the configured repositories and their
     * contents, the list is not processed by the {@link RepositorySystemSession#getVersionFilter() session's version
     * filter}.
     * <p>
     * The supplied request may also refer to a single concrete version rather than a version range. In this case
     * though, the result contains simply the (parsed) input version, regardless of the repositories and their contents.
     * 
     * @param session The repository session, must not be {@code null}.
     * @param request The version range request, must not be {@code null}.
     * @return The version range result, never {@code null}.
     * @throws VersionRangeResolutionException If the requested range could not be parsed. Note that an empty range does
     *             not raise an exception.
     * @see RepositorySystem#resolveVersionRange(RepositorySystemSession, VersionRangeRequest)
     */
    VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException;

}
