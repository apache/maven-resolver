/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.utils.URIUtils;

/**
 * Helps to deal with URIs.
 */
final class UriUtils
{

    public static URI resolve( URI base, URI ref )
    {
        String path = ref.getRawPath();
        if ( path != null && path.length() > 0 )
        {
            path = base.getRawPath();
            if ( path == null || !path.endsWith( "/" ) )
            {
                try
                {
                    base = new URI( base.getScheme(), base.getAuthority(), base.getPath() + '/', null, null );
                }
                catch ( URISyntaxException e )
                {
                    throw new IllegalStateException( e );
                }
            }
        }
        return URIUtils.resolve( base, ref );
    }

    public static List<URI> getDirectories( URI base, URI uri )
    {
        List<URI> dirs = new ArrayList<URI>();
        for ( URI dir = uri.resolve( "." ); !isBase( base, dir ); dir = dir.resolve( ".." ) )
        {
            dirs.add( dir );
        }
        return dirs;
    }

    private static boolean isBase( URI base, URI uri )
    {
        String path = uri.getRawPath();
        if ( path == null || "/".equals( path ) )
        {
            return true;
        }
        if ( base != null )
        {
            URI rel = base.relativize( uri );
            if ( rel.getRawPath() == null || rel.getRawPath().length() <= 0 || rel.equals( uri ) )
            {
                return true;
            }
        }
        return false;
    }

}
