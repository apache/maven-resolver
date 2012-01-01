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
package org.eclipse.aether;

import static org.junit.Assert.*;

import org.eclipse.aether.repository.Authentication;
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

        RemoteRepository repo = new RemoteRepository( "id", "default", "void" );
        assertSame( null, session.getProxySelector().getProxy( repo ) );

        repo.setProxy( new Proxy( "http", "localhost", 8080, null ) );
        assertSame( repo.getProxy(), session.getProxySelector().getProxy( repo ) );
    }

    @Test
    public void testDefaultAuthenticationSelectorUsesExistingAuth()
    {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        RemoteRepository repo = new RemoteRepository( "id", "default", "void" );
        assertSame( null, session.getAuthenticationSelector().getAuthentication( repo ) );

        repo.setAuthentication( new Authentication( "user", "pass" ) );
        assertSame( repo.getAuthentication(), session.getAuthenticationSelector().getAuthentication( repo ) );
    }

    @Test
    public void testCopyConstructorCopiesPropertiesDeep()
    {
        DefaultRepositorySystemSession session1 = new DefaultRepositorySystemSession();
        session1.setUserProps( System.getProperties() );
        session1.setSystemProps( System.getProperties() );
        session1.setConfigProps( System.getProperties() );

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
