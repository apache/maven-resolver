package org.eclipse.aether;

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;
import org.mockito.Mockito;

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


    @Test
    public void testCopyRepositorySystemSession() throws Exception
    {
        RepositorySystemSession session = Mockito.mock( RepositorySystemSession.class, Mockito.RETURNS_MOCKS );

        RepositorySystemSession newSession = new DefaultRepositorySystemSession( session );

        Method[] methods = RepositorySystemSession.class.getMethods();

        for ( Method method : methods )
        {
            assertEquals( method.getName(), method.invoke( session ) == null, method.invoke( newSession ) == null );
        }
    }

}
