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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;

/* *
 */
class StubVersionResolver
    implements VersionResolver
{

    public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
        throws VersionResolutionException
    {
        VersionResult result = new VersionResult( request ).setVersion( request.getArtifact().getVersion() );
        if ( request.getRepositories().size() > 0 )
        {
            result = result.setRepository( request.getRepositories().get( 0 ) );
        }
        return result;

    }

}
