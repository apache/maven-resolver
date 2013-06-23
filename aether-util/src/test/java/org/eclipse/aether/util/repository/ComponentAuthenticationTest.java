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
package org.eclipse.aether.util.repository;

import static org.junit.Assert.*;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

public class ComponentAuthenticationTest
{

    private static class Component
    {
    }

    private RepositorySystemSession newSession()
    {
        return new DefaultRepositorySystemSession();
    }

    private RemoteRepository newRepo( Authentication auth )
    {
        return new RemoteRepository.Builder( "test", "default", "http://localhost" ).setAuthentication( auth ).build();
    }

    private AuthenticationContext newContext( Authentication auth )
    {
        return AuthenticationContext.forRepository( newSession(), newRepo( auth ) );
    }

    private String newDigest( Authentication auth )
    {
        return AuthenticationDigest.forRepository( newSession(), newRepo( auth ) );
    }

    @Test
    public void testFill()
    {
        Component comp = new Component();
        Authentication auth = new ComponentAuthentication( "key", comp );
        AuthenticationContext context = newContext( auth );
        assertEquals( null, context.get( "another-key" ) );
        assertSame( comp, context.get( "key", Component.class ) );
    }

    @Test
    public void testDigest()
    {
        Authentication auth1 = new ComponentAuthentication( "key", new Component() );
        Authentication auth2 = new ComponentAuthentication( "key", new Component() );
        String digest1 = newDigest( auth1 );
        String digest2 = newDigest( auth2 );
        assertEquals( digest1, digest2 );

        Authentication auth3 = new ComponentAuthentication( "key", new Object() );
        String digest3 = newDigest( auth3 );
        assertFalse( digest3.equals( digest1 ) );

        Authentication auth4 = new ComponentAuthentication( "Key", new Component() );
        String digest4 = newDigest( auth4 );
        assertFalse( digest4.equals( digest1 ) );
    }

    @Test
    public void testEquals()
    {
        Authentication auth1 = new ComponentAuthentication( "key", new Component() );
        Authentication auth2 = new ComponentAuthentication( "key", new Component() );
        Authentication auth3 = new ComponentAuthentication( "key", new Object() );
        assertEquals( auth1, auth2 );
        assertFalse( auth1.equals( auth3 ) );
        assertFalse( auth1.equals( null ) );
    }

    @Test
    public void testHashCode()
    {
        Authentication auth1 = new ComponentAuthentication( "key", new Component() );
        Authentication auth2 = new ComponentAuthentication( "key", new Component() );
        assertEquals( auth1.hashCode(), auth2.hashCode() );
    }

}
