/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

import static org.junit.Assert.*;

import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

/**
 */
public class DefaultRepositorySystemSessionTest
{

    @Test
    public void testDefaultProxySelectorUsesExistingProxy()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        RemoteRepository repo = new RemoteRepository.Builder( "id", "default", "void" ).build();
        assertSame( null, session.getProxySelector().getProxy( repo ) );

        Proxy proxy = new Proxy( "http", "localhost", 8080, null );
        repo = new RemoteRepository.Builder( repo ).setProxy( proxy ).build();
        assertSame( proxy, session.getProxySelector().getProxy( repo ) );
    }

    @Test
    public void testDefaultAuthenticationSelectorUsesExistingAuth()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        RemoteRepository repo = new RemoteRepository.Builder( "id", "default", "void" ).build();
        assertSame( null, session.getAuthenticationSelector().getAuthentication( repo ) );

        Authentication auth = new Authentication()
        {
            public void fill( AuthenticationContext context, String key, Map<String, String> data )
            {
            }

            public void digest( AuthenticationDigest digest )
            {
            }
        };
        repo = new RemoteRepository.Builder( repo ).setAuthentication( auth ).build();
        assertSame( auth, session.getAuthenticationSelector().getAuthentication( repo ) );
    }

    @Test
    public void testCopyConstructorCopiesPropertiesDeep()
    {
        DefaultRepositorySystemSession session1 = new DefaultRepositorySystemSession();
        session1.setUserProperties( System.getProperties() );
        session1.setSystemProperties( System.getProperties() );
        session1.setConfigProperties( System.getProperties() );

        DefaultRepositorySystemSession session2 = new DefaultRepositorySystemSession( session1 );
        session2.setUserProperty( "key", "test" );
        session2.setSystemProperty( "key", "test" );
        session2.setConfigProperty( "key", "test" );

        assertEquals( null, session1.getUserProperties().get( "key" ) );
        assertEquals( null, session1.getSystemProperties().get( "key" ) );
        assertEquals( null, session1.getConfigProperties().get( "key" ) );
    }

    @Test
    public void testReadOnlyProperties()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        try
        {
            session.getUserProperties().put( "key", "test" );
            fail( "user properties are modifiable" );
        }
        catch ( UnsupportedOperationException e )
        {
            // expected
        }

        try
        {
            session.getSystemProperties().put( "key", "test" );
            fail( "system properties are modifiable" );
        }
        catch ( UnsupportedOperationException e )
        {
            // expected
        }

        try
        {
            session.getConfigProperties().put( "key", "test" );
            fail( "config properties are modifiable" );
        }
        catch ( UnsupportedOperationException e )
        {
            // expected
        }
    }

}
