package org.apache.maven.resolver.repository;

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

import static org.junit.Assert.*;

import java.util.Map;

import org.apache.maven.resolver.DefaultRepositorySystemSession;
import org.apache.maven.resolver.RepositorySystemSession;
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
