/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.repository;

import static org.junit.Assert.*;

import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Test;

public class AuthenticationDigestTest
{

    private RepositorySystemSession newSession()
    {
        return new DefaultRepositorySystemSession();
    }

    private RemoteRepository newRepo( Authentication auth, Proxy proxy )
    {
        return new RemoteRepository.Builder( "test", "default", "http://localhost" ) //
        .setAuthentication( auth ).setProxy( proxy ).build();
    }

    private Proxy newProxy( Authentication auth )
    {
        return new Proxy( Proxy.TYPE_HTTP, "localhost", 8080, auth );
    }

    @Test
    public void testForRepository()
    {
        final RepositorySystemSession session = newSession();
        final RemoteRepository[] repos = { null };

        Authentication auth = new Authentication()
        {
            public void fill( AuthenticationContext context, String key, Map<String, String> data )
            {
                fail( "AuthenticationDigest should not call fill()" );
            }

            public void digest( AuthenticationDigest digest )
            {
                assertNotNull( digest );
                assertSame( session, digest.getSession() );
                assertNotNull( digest.getRepository() );
                assertNull( digest.getProxy() );
                assertNull( "digest() should only be called once", repos[0] );
                repos[0] = digest.getRepository();

                digest.update( (byte[]) null );
                digest.update( (char[]) null );
                digest.update( (String[]) null );
                digest.update( null, null );
            }
        };

        RemoteRepository repo = newRepo( auth, newProxy( null ) );

        String digest = AuthenticationDigest.forRepository( session, repo );
        assertSame( repo, repos[0] );
        assertNotNull( digest );
        assertTrue( digest.length() > 0 );
    }

    @Test
    public void testForRepository_NoAuth()
    {
        RemoteRepository repo = newRepo( null, null );

        String digest = AuthenticationDigest.forRepository( newSession(), repo );
        assertEquals( "", digest );
    }

    @Test
    public void testForProxy()
    {
        final RepositorySystemSession session = newSession();
        final Proxy[] proxies = { null };

        Authentication auth = new Authentication()
        {
            public void fill( AuthenticationContext context, String key, Map<String, String> data )
            {
                fail( "AuthenticationDigest should not call fill()" );
            }

            public void digest( AuthenticationDigest digest )
            {
                assertNotNull( digest );
                assertSame( session, digest.getSession() );
                assertNotNull( digest.getRepository() );
                assertNotNull( digest.getProxy() );
                assertNull( "digest() should only be called once", proxies[0] );
                proxies[0] = digest.getProxy();

                digest.update( (byte[]) null );
                digest.update( (char[]) null );
                digest.update( (String[]) null );
                digest.update( null, null );
            }
        };

        Proxy proxy = newProxy( auth );

        String digest = AuthenticationDigest.forProxy( session, newRepo( null, proxy ) );
        assertSame( proxy, proxies[0] );
        assertNotNull( digest );
        assertTrue( digest.length() > 0 );
    }

    @Test
    public void testForProxy_NoProxy()
    {
        RemoteRepository repo = newRepo( null, null );

        String digest = AuthenticationDigest.forProxy( newSession(), repo );
        assertEquals( "", digest );
    }

    @Test
    public void testForProxy_NoProxyAuth()
    {
        RemoteRepository repo = newRepo( null, newProxy( null ) );

        String digest = AuthenticationDigest.forProxy( newSession(), repo );
        assertEquals( "", digest );
    }

}
