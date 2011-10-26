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
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 */
public interface VersionRangeResolver
{

    /**
     * Expands a version range to a list of matching versions, in ascending order. For example, resolves "[3.8,4.0)" to
     * ["3.8", "3.8.1", "3.8.2"].
     */
    VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException;

}
