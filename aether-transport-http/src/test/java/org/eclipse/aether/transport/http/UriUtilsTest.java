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

import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class UriUtilsTest
{

    private String resolve( URI base, String ref )
    {
        return UriUtils.resolve( base, URI.create( ref ) ).toString();
    }

    @Test
    public void testResolve_BaseEmptyPath()
    {
        URI base = URI.create( "http://host" );
        assertEquals( "http://host/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BaseRootPath()
    {
        URI base = URI.create( "http://host/" );
        assertEquals( "http://host/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BasePathTrailingSlash()
    {
        URI base = URI.create( "http://host/sub/dir/" );
        assertEquals( "http://host/sub/dir/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/sub/dir/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/sub/dir/?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/sub/dir/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/sub/dir/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BasePathNoTrailingSlash()
    {
        URI base = URI.create( "http://host/sub/d%20r" );
        assertEquals( "http://host/sub/d%20r/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/sub/d%20r/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/sub/d%20r?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/sub/d%20r/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/sub/d%20r/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    private List<URI> getDirs( String base, String uri )
    {
        return UriUtils.getDirectories( ( base != null ) ? URI.create( base ) : null, URI.create( uri ) );
    }

    private void assertUris( List<URI> actual, String... expected )
    {
        List<String> uris = new ArrayList<String>( actual.size() );
        for ( URI uri : actual )
        {
            uris.add( uri.toString() );
        }
        assertEquals( Arrays.asList( expected ), uris );
    }

    @Test
    public void testGetDirectories_NoBase()
    {
        List<URI> parents = getDirs( null, "http://host/repo/sub/dir/file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/", "http://host/repo/" );

        parents = getDirs( null, "http://host/repo/sub/dir/?file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/", "http://host/repo/" );

        parents = getDirs( null, "http://host/" );
        assertUris( parents );
    }

    @Test
    public void testGetDirectories_ExplicitBaseTrailingSlash()
    {
        List<URI> parents = getDirs( "http://host/repo/", "http://host/repo/sub/dir/file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/" );

        parents = getDirs( "http://host/repo/", "http://host/repo/sub/dir/?file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/" );

        parents = getDirs( "http://host/repo/", "http://host/" );
        assertUris( parents );
    }

    @Test
    public void testGetDirectories_ExplicitBaseNoTrailingSlash()
    {
        List<URI> parents = getDirs( "http://host/repo", "http://host/repo/sub/dir/file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/" );

        parents = getDirs( "http://host/repo", "http://host/repo/sub/dir/?file.jar" );
        assertUris( parents, "http://host/repo/sub/dir/", "http://host/repo/sub/" );

        parents = getDirs( "http://host/repo", "http://host/" );
        assertUris( parents );
    }

}
