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
package org.eclipse.aether.util.repository;

import static org.junit.Assert.*;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

public class SecretAuthenticationTest
{

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
    public void testConstructor_CopyChars()
    {
        char[] value = { 'v', 'a', 'l' };
        new SecretAuthentication( "key", value );
        assertArrayEquals( new char[] { 'v', 'a', 'l' }, value );
    }

    @Test
    public void testFill()
    {
        Authentication auth = new SecretAuthentication( "key", "value" );
        AuthenticationContext context = newContext( auth );
        assertEquals( null, context.get( "another-key" ) );
        assertEquals( "value", context.get( "key" ) );
    }

    @Test
    public void testDigest()
    {
        Authentication auth1 = new SecretAuthentication( "key", "value" );
        Authentication auth2 = new SecretAuthentication( "key", "value" );
        String digest1 = newDigest( auth1 );
        String digest2 = newDigest( auth2 );
        assertEquals( digest1, digest2 );

        Authentication auth3 = new SecretAuthentication( "key", "Value" );
        String digest3 = newDigest( auth3 );
        assertFalse( digest3.equals( digest1 ) );

        Authentication auth4 = new SecretAuthentication( "Key", "value" );
        String digest4 = newDigest( auth4 );
        assertFalse( digest4.equals( digest1 ) );
    }

    @Test
    public void testEquals()
    {
        Authentication auth1 = new SecretAuthentication( "key", "value" );
        Authentication auth2 = new SecretAuthentication( "key", "value" );
        Authentication auth3 = new SecretAuthentication( "key", "Value" );
        assertEquals( auth1, auth2 );
        assertFalse( auth1.equals( auth3 ) );
        assertFalse( auth1.equals( null ) );
    }

    @Test
    public void testHashCode()
    {
        Authentication auth1 = new SecretAuthentication( "key", "value" );
        Authentication auth2 = new SecretAuthentication( "key", "value" );
        assertEquals( auth1.hashCode(), auth2.hashCode() );
    }

}
