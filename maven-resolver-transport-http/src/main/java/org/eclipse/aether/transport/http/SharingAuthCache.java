package org.eclipse.aether.transport.http;

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

    SharingAuthCache( LocalState state )
    {
        this.state = state;
        authSchemes = new HashMap<>();
    }

    @SuppressWarnings( "checkstyle:magicnumber" )
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
