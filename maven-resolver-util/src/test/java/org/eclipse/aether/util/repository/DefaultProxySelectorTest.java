package org.eclipse.aether.util.repository;

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

import java.util.Arrays;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultProxySelector.NonProxyHosts;
import org.junit.Test;

/**
 */
public class DefaultProxySelectorTest
{

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( "id", "type", url ).build();
    }

    private boolean isNonProxyHost( String host, String nonProxyHosts )
    {
        return new NonProxyHosts( NonProxyHosts.split( nonProxyHosts ) ).isNonProxyHost( host );
    }

    private boolean isNonProxyHost( String host, String... nonProxyHosts )
    {
        return new NonProxyHosts( nonProxyHosts != null ? Arrays.asList( nonProxyHosts ) : null ).isNonProxyHost( host );
    }

    @Test
    public void testIsNonProxyHost_Blank()
    {
        assertFalse( isNonProxyHost( "www.eclipse.org", (String) null ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "" ) );
        assertFalse( isNonProxyHost( "", "" ) );

        assertFalse( isNonProxyHost( "www.eclipse.org", (String[]) null ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", new String[0] ) );
        assertFalse( isNonProxyHost( "", new String[0] ) );
        assertFalse( isNonProxyHost( "", new String[] { null } ) );
        assertFalse( isNonProxyHost( "", new String[] { "" } ) );
    }

    @Test
    public void testIsNonProxyHost_Wildcard()
    {
        assertTrue( isNonProxyHost( "www.eclipse.org", "*" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "*.org" ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "*.com" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.*" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.*.org" ) );
    }

    @Test
    public void testIsNonProxyHost_Multiple()
    {
        assertTrue( isNonProxyHost( "eclipse.org", "eclipse.org|host2" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1|eclipse.org" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1|eclipse.org|host2" ) );

        assertTrue( isNonProxyHost( "eclipse.org", "eclipse.org,host2" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1,eclipse.org" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1,eclipse.org,host2" ) );

        assertFalse( isNonProxyHost( "", "||host||" ) );
        assertFalse( isNonProxyHost( "", ",,host,," ) );
    }

    @Test
    public void testIsNonProxyHost_Trimming()
    {
        assertFalse( isNonProxyHost( "", " " ) );
        assertTrue( isNonProxyHost( "eclipse.org", " eclipse.org " ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1| eclipse.org |host2" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1|eclipse.org " ) );
        assertTrue( isNonProxyHost( "eclipse.org", " eclipse.org|host2" ) );
    }

    @Test
    public void testIsNonProxyHost_Misc()
    {
        assertFalse( isNonProxyHost( "www.eclipse.org", "www.eclipse.com" ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "eclipse.org" ) );
    }

    @Test
    public void testIsNonProxyHost_CaseInsensitivity()
    {
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.ECLIPSE.org" ) );
        assertTrue( isNonProxyHost( "www.ECLIPSE.org", "www.eclipse.org" ) );
    }

    @Test
    public void testGetProxy_FirstMatchWins()
    {
        DefaultProxySelector selector = new DefaultProxySelector();
        Proxy proxy1 = new Proxy( Proxy.TYPE_HTTP, "proxy", 88 );
        selector.add( proxy1, "localhost" );
        Proxy proxy2 = new Proxy( Proxy.TYPE_HTTP, "other", 8888 );
        selector.add( proxy2, "" );

        assertSame( proxy1, selector.getProxy( newRepo( "http://eclipse.org/" ) ) );
        assertSame( proxy2, selector.getProxy( newRepo( "http://localhost/" ) ) );
    }

    @Test
    public void testGetProxy_Http()
    {
        DefaultProxySelector selector = new DefaultProxySelector();
        Proxy proxy1 = new Proxy( Proxy.TYPE_HTTP, "proxy", 88 );
        selector.add( proxy1, "localhost" );

        assertSame( proxy1, selector.getProxy( newRepo( "http://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "HTTP://eclipse.org/" ) ) );

        assertSame( proxy1, selector.getProxy( newRepo( "https://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "HTTPS://eclipse.org/" ) ) );

        assertNull( selector.getProxy( newRepo( "http://localhost/" ) ) );

        Proxy proxy2 = new Proxy( Proxy.TYPE_HTTPS, "sproxy", 888 );
        selector.add( proxy2, "localhost" );

        assertSame( proxy1, selector.getProxy( newRepo( "http://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "HTTP://eclipse.org/" ) ) );

        assertSame( proxy2, selector.getProxy( newRepo( "https://eclipse.org/" ) ) );
        assertSame( proxy2, selector.getProxy( newRepo( "HTTPS://eclipse.org/" ) ) );
    }

    @Test
    public void testGetProxy_WebDav()
    {
        DefaultProxySelector selector = new DefaultProxySelector();
        Proxy proxy1 = new Proxy( Proxy.TYPE_HTTP, "proxy", 88 );
        selector.add( proxy1, "localhost" );

        assertSame( proxy1, selector.getProxy( newRepo( "dav://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "dav:http://eclipse.org/" ) ) );

        assertSame( proxy1, selector.getProxy( newRepo( "davs://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "dav:https://eclipse.org/" ) ) );

        assertNull( selector.getProxy( newRepo( "dav://localhost/" ) ) );

        Proxy proxy2 = new Proxy( Proxy.TYPE_HTTPS, "sproxy", 888 );
        selector.add( proxy2, "localhost" );

        assertSame( proxy1, selector.getProxy( newRepo( "dav://eclipse.org/" ) ) );
        assertSame( proxy1, selector.getProxy( newRepo( "dav:http://eclipse.org/" ) ) );

        assertSame( proxy2, selector.getProxy( newRepo( "davs://eclipse.org/" ) ) );
        assertSame( proxy2, selector.getProxy( newRepo( "dav:https://eclipse.org/" ) ) );
    }

}
