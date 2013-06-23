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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;

/**
 * Auth cache that can be shared among multiple threads.
 */
final class ConcurrentAuthCache
    implements AuthCache
{

    private final Map<HttpHost, AuthScheme> schemes;

    public ConcurrentAuthCache()
    {
        schemes = new ConcurrentHashMap<HttpHost, AuthScheme>();
    }

    private HttpHost toKey( HttpHost host )
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
        return schemes.get( toKey( host ) );
    }

    public void put( HttpHost host, AuthScheme authScheme )
    {
        if ( authScheme != null )
        {
            schemes.put( toKey( host ), authScheme );
        }
        else
        {
            remove( host );
        }
    }

    public void remove( HttpHost host )
    {
        schemes.remove( toKey( host ) );
    }

    public void clear()
    {
        schemes.clear();
    }

    @Override
    public String toString()
    {
        return schemes.toString();
    }

}
