/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultMirrorSelector.*;
import org.junit.Test;

/**
 */
public class DefaultMirrorSelectorTest
{

    private RemoteRepository newRepo( String id, String type, String url )
    {
        return new RemoteRepository.Builder( id, type, url ).build();
    }

    private static boolean matchesType( String repoType, String mirrorType )
    {
        return new TypeMatcher( DefaultMirrorSelector.split( mirrorType ) ).isMatch( repoType );
    }

    private boolean matchesPattern( RemoteRepository repository, String pattern )
    {
        return new IdMatcher( DefaultMirrorSelector.split( pattern ) ).isMatch( repository );
    }

    private static boolean isExternalRepo( RemoteRepository repository )
    {
        return IdMatcher.isExternalRepo( repository );
    }

    @Test
    public void testIsExternalRepo()
    {
        assertEquals( false, isExternalRepo( newRepo( "", "", "http://localhost/path" ) ) );
        assertEquals( false, isExternalRepo( newRepo( "", "", "http://127.0.0.1/path" ) ) );
        assertEquals( false, isExternalRepo( newRepo( "", "", "file:/path" ) ) );
        assertEquals( true, isExternalRepo( newRepo( "", "", "http://eclipse.org/" ) ) );
        assertEquals( true, isExternalRepo( newRepo( "", "", "" ) ) );
    }

    @Test
    public void testMatchesType()
    {
        assertEquals( true, matchesType( null, null ) );
        assertEquals( true, matchesType( "any", null ) );
        assertEquals( true, matchesType( "any", "" ) );
        assertEquals( true, matchesType( "any", "*" ) );

        assertEquals( false, matchesType( null, "default" ) );
        assertEquals( false, matchesType( "", "default" ) );
        assertEquals( false, matchesType( "any", "default" ) );

        assertEquals( true, matchesType( "default", "default" ) );

        assertEquals( false, matchesType( "default", "DEFAULT" ) );
        assertEquals( false, matchesType( "DEFAULT", "default" ) );

        assertEquals( true, matchesType( "default", "default,foo" ) );
        assertEquals( true, matchesType( "default", "foo,default" ) );

        assertEquals( true, matchesType( "default", "*,foo" ) );
        assertEquals( true, matchesType( "default", "foo,*" ) );

        assertEquals( false, matchesType( "p2", "*,!p2" ) );
        assertEquals( false, matchesType( "p2", "!p2,*" ) );
    }

    @Test
    public void testMatchesType_Trimming()
    {
        assertEquals( true, matchesType( "any", " " ) );
        assertEquals( true, matchesType( "default", " default " ) );
        assertEquals( true, matchesType( "default", "foo, default ,bar" ) );
        assertEquals( true, matchesType( "default", " default ,bar" ) );
        assertEquals( true, matchesType( "default", "foo, default " ) );
    }

    @Test
    public void testMatchesPattern()
    {
        assertEquals( false, matchesPattern( newRepo( "id", "type", "url" ), null ) );
        assertEquals( false, matchesPattern( newRepo( "id", "type", "url" ), "" ) );
        assertEquals( false, matchesPattern( newRepo( "id", "type", "url" ), "central" ) );

        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "central" ) );

        assertEquals( false, matchesPattern( newRepo( "central", "type", "url" ), "CENTRAL" ) );
        assertEquals( false, matchesPattern( newRepo( "CENTRAL", "type", "url" ), "central" ) );

        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "central,foo" ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "foo,central" ) );

        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "*,foo" ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "foo,*" ) );

        assertEquals( false, matchesPattern( newRepo( "central", "type", "url" ), "*,!central" ) );
        assertEquals( false, matchesPattern( newRepo( "central", "type", "url" ), "!central,*" ) );
    }

    @Test
    public void testMatchesPattern_Trimming()
    {
        assertEquals( false, matchesPattern( newRepo( "central", "type", "url" ), " " ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), " central " ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "foo, central ,bar" ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), " central ,bar" ) );
        assertEquals( true, matchesPattern( newRepo( "central", "type", "url" ), "foo, central " ) );
    }

    @Test
    public void testGetMirror_FirstMatchWins()
    {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        selector.add( "mirror1", "http://host1", "default", false, "*", "*" );
        selector.add( "mirror2", "http://host2", "default", false, "*", "*" );
        RemoteRepository mirror = selector.getMirror( newRepo( "central", "default", "http://localhost" ) );
        assertNotNull( mirror );
        assertEquals( "mirror1", mirror.getId() );
        assertEquals( "http://host1", mirror.getUrl() );
    }

    @Test
    public void testGetMirror_ExactMatchWins()
    {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        selector.add( "mirror1", "http://host1", "default", false, "*", "*" );
        selector.add( "mirror2", "http://host2", "default", false, "central", "*" );
        selector.add( "mirror3", "http://host3", "default", false, "*", "*" );
        RemoteRepository mirror = selector.getMirror( newRepo( "central", "default", "http://localhost" ) );
        assertNotNull( mirror );
        assertEquals( "mirror2", mirror.getId() );
        assertEquals( "http://host2", mirror.getUrl() );
    }

    @Test
    public void testGetMirror_WithoutMirrorLayout()
    {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        selector.add( "mirror", "http://host", "", false, "*", "*" );
        RemoteRepository mirror = selector.getMirror( newRepo( "central", "default", "http://localhost" ) );
        assertNotNull( mirror );
        assertEquals( "mirror", mirror.getId() );
        assertEquals( "http://host", mirror.getUrl() );
        assertEquals( "default", mirror.getContentType() );
    }

    @Test
    public void testGetMirror_WithMirrorLayout()
    {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        selector.add( "mirror", "http://host", "layout", false, "*", "*" );
        RemoteRepository mirror = selector.getMirror( newRepo( "central", "default", "http://localhost" ) );
        assertNotNull( mirror );
        assertEquals( "mirror", mirror.getId() );
        assertEquals( "http://host", mirror.getUrl() );
        assertEquals( "layout", mirror.getContentType() );
    }

    @Test
    public void testGetMirror_MirroredRepos()
    {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        selector.add( "mirror", "http://host", "layout", false, "*", "*" );
        RemoteRepository repo = newRepo( "central", "default", "http://localhost" );
        RemoteRepository mirror = selector.getMirror( repo );
        assertNotNull( mirror );
        assertEquals( Arrays.asList( repo ), mirror.getMirroredRepositories() );
    }

}
