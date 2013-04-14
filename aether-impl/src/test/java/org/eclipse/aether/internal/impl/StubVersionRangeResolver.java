/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionScheme;

/**
 */
public class StubVersionRangeResolver
    implements VersionRangeResolver
{

    private final VersionScheme versionScheme = new GenericVersionScheme();

    public VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException
    {
        VersionRangeResult result = new VersionRangeResult( request );
        try
        {
            VersionConstraint constraint = versionScheme.parseVersionConstraint( request.getArtifact().getVersion() );
            result.setVersionConstraint( constraint );
            if ( constraint.getRange() == null )
            {
                result.addVersion( constraint.getVersion() );
            }
            else
            {
                for ( int i = 1; i < 10; i++ )
                {
                    Version ver = versionScheme.parseVersion( Integer.toString( i ) );
                    if ( constraint.containsVersion( ver ) )
                    {
                        result.addVersion( ver );
                        if ( !request.getRepositories().isEmpty() )
                        {
                            result.setRepository( ver, request.getRepositories().get( 0 ) );
                        }
                    }
                }
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
