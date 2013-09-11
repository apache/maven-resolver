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

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JreProxySelectorTest
{

    private abstract class AbstractProxySelector
        extends java.net.ProxySelector
    {
        @Override
        public void connectFailed( URI uri, SocketAddress sa, IOException ioe )
        {
        }
    }

    private ProxySelector selector = new JreProxySelector();

    private java.net.ProxySelector original;

    @Before
    public void init()
    {
        original = java.net.ProxySelector.getDefault();
    }

    @After
    public void exit()
    {
        java.net.ProxySelector.setDefault( original );
        Authenticator.setDefault( null );
    }

    @Test
    public void testGetProxy_InvalidUrl()
        throws Exception
    {
        RemoteRepository repo = new RemoteRepository.Builder( "test", "default", "http://host:invalid" ).build();
        assertNull( selector.getProxy( repo ) );
    }

    @Test
    public void testGetProxy_OpaqueUrl()
        throws Exception
    {
        RemoteRepository repo = new RemoteRepository.Builder( "test", "default", "classpath:base" ).build();
        assertNull( selector.getProxy( repo ) );
    }

    @Test
    public void testGetProxy_NullSelector()
        throws Exception
    {
        RemoteRepository repo = new RemoteRepository.Builder( "test", "default", "http://repo.eclipse.org/" ).build();
        java.net.ProxySelector.setDefault( null );
        assertNull( selector.getProxy( repo ) );
    }

    @Test
    public void testGetProxy_NoProxies()
        throws Exception
    {
        RemoteRepository repo = new RemoteRepository.Builder( "test", "default", "http://repo.eclipse.org/" ).build();
        java.net.ProxySelector.setDefault( new AbstractProxySelector()
        {
            @Override
            public List<java.net.Proxy> select( URI uri )
            {
                return Collections.emptyList();
            }

        } );
        assertNull( selector.getProxy( repo ) );
    }

    @Test
    public void testGetProxy_DirectProxy()
        throws Exception
    {
        RemoteRepository repo = new RemoteRepository.Builder( "test", "default", "http://repo.eclipse.org/" ).build();
        final InetSocketAddress addr = InetSocketAddress.createUnresolved( "proxy", 8080 );
        java.net.ProxySelector.setDefault( new AbstractProxySelector()
        {
            @Override
            public List<java.net.Proxy> select( URI uri )
            {
                return Arrays.asList( java.net.Proxy.NO_PROXY, new java.net.Proxy( java.net.Proxy.Type.HTTP, addr ) );
            }

        } );
        assertNull( selector.getProxy( repo ) );
    }

    @Test
    public void testGetProxy_HttpProxy()
        throws Exception
    {
        final RemoteRepository repo =
            new RemoteRepository.Builder( "test", "default", "http://repo.eclipse.org/" ).build();
        final URL url = new URL( repo.getUrl() );
        final InetSocketAddress addr = InetSocketAddress.createUnresolved( "proxy", 8080 );
        java.net.ProxySelector.setDefault( new AbstractProxySelector()
        {
            @Override
            public List<java.net.Proxy> select( URI uri )
            {
                if ( repo.getHost().equalsIgnoreCase( uri.getHost() ) )
                {
                    return Arrays.asList( new java.net.Proxy( java.net.Proxy.Type.HTTP, addr ) );
                }
                return Collections.emptyList();
            }

        } );
        Authenticator.setDefault( new Authenticator()
        {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                if ( Authenticator.RequestorType.PROXY.equals( getRequestorType() )
                    && addr.getHostName().equals( getRequestingHost() ) && addr.getPort() == getRequestingPort()
                    && url.equals( getRequestingURL() ) )
                {
                    return new PasswordAuthentication( "proxyuser", "proxypass".toCharArray() );
                }
                return super.getPasswordAuthentication();
            }
        } );

        Proxy proxy = selector.getProxy( repo );
        assertNotNull( proxy );
        assertEquals( addr.getHostName(), proxy.getHost() );
        assertEquals( addr.getPort(), proxy.getPort() );
        assertEquals( Proxy.TYPE_HTTP, proxy.getType() );

        RemoteRepository repo2 = new RemoteRepository.Builder( repo ).setProxy( proxy ).build();
        Authentication auth = proxy.getAuthentication();
        assertNotNull( auth );
        AuthenticationContext authCtx = AuthenticationContext.forProxy( new DefaultRepositorySystemSession(), repo2 );
        assertEquals( "proxyuser", authCtx.get( AuthenticationContext.USERNAME ) );
        assertEquals( "proxypass", authCtx.get( AuthenticationContext.PASSWORD ) );
    }

}
