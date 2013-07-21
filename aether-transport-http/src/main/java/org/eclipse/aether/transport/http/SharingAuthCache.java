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
package org.eclipse.aether.transport.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;

/**
 * Auth scheme cache that upon clearing releases all cached schemes into a pool for future reuse by other requests,
 * thereby reducing challenge-response roundtrips.
 */
final class SharingAuthCache
    implements AuthCache
{

    private final LocalState state;

    private final Map<HttpHost, AuthScheme> authSchemes;

    public SharingAuthCache( LocalState state )
    {
        this.state = state;
        authSchemes = new HashMap<HttpHost, AuthScheme>();
    }

    private static HttpHost toKey( HttpHost host )
    {
        if ( host.getPort() <= 0 )
        {
            int port = host.getSchemeName().equalsIgnoreCase( "https" ) ? 443 : 80;
            return new HttpHost( host.getHostName(), port, host.getSchemeName() );
        }
        return host;
    }

    public AuthScheme get( HttpHost host )
    {
        host = toKey( host );
        AuthScheme authScheme = authSchemes.get( host );
        if ( authScheme == null )
        {
            authScheme = state.getAuthScheme( host );
            authSchemes.put( host, authScheme );
        }
        return authScheme;
    }

    public void put( HttpHost host, AuthScheme authScheme )
    {
        if ( authScheme != null )
        {
            authSchemes.put( toKey( host ), authScheme );
        }
        else
        {
            remove( host );
        }
    }

    public void remove( HttpHost host )
    {
        authSchemes.remove( toKey( host ) );
    }

    public void clear()
    {
        share();
        authSchemes.clear();
    }

    private void share()
    {
        for ( Map.Entry<HttpHost, AuthScheme> entry : authSchemes.entrySet() )
        {
            state.setAuthScheme( entry.getKey(), entry.getValue() );
        }
    }

    @Override
    public String toString()
    {
        return authSchemes.toString();
    }

}
