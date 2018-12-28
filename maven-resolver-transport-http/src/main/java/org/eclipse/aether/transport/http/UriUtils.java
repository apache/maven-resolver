package org.eclipse.aether.transport.http;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
        List<URI> dirs = new ArrayList<>();
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
