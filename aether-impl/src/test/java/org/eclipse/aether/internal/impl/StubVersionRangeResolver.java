/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
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
import org.eclipse.aether.internal.test.util.impl.TestVersionScheme;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

/**
 */
public class StubVersionRangeResolver
    implements VersionRangeResolver
{

    private final VersionScheme versionScheme = new TestVersionScheme();

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
        try
        {
            Version ver = versionScheme.parseVersion( version );
            result.setVersionConstraint( versionScheme.parseVersionConstraint( request.getArtifact().getVersion() ) );
            result.setVersions( Arrays.asList( (Version) ver ) );
            if ( range && !request.getRepositories().isEmpty() )
            {
                result.setRepository( ver, request.getRepositories().get( 0 ) );
            }
        }
        catch ( InvalidVersionSpecificationException e )
        {
            result.addException( e );
            throw new VersionRangeResolutionException( result );
        }

        return result;
    }

}
