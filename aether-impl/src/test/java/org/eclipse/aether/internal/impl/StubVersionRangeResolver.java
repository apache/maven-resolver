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
package org.eclipse.aether.internal.impl;

import java.util.Arrays;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.test.util.impl.StubVersion;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 */
public class StubVersionRangeResolver
    implements VersionRangeResolver
{

    public VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException
    {
        String version = request.getArtifact().getVersion();
        boolean range = false;

        if ( version.matches( "\\[[^,]+,.*" ) )
        {
            version = version.substring( 1, version.indexOf( ',', 1 ) );
            range = true;
        }

        VersionRangeResult result = new VersionRangeResult( request );
        StubVersion ver = new StubVersion( version );
        result.setVersions( Arrays.asList( (Version) ver ) );
        if ( range && !request.getRepositories().isEmpty() )
        {
            result.setRepository( ver, request.getRepositories().get( 0 ) );
        }

        return result;
    }

}
