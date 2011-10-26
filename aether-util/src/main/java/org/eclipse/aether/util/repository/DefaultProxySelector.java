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
package org.eclipse.aether.util.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultProxySelector;

/**
 * A simple proxy selector that selects the first matching proxy from a list of configured proxies.
 */
public final class DefaultProxySelector
    implements ProxySelector
{

    private List<ProxyDef> proxies = new ArrayList<ProxyDef>();

    /**
     * Adds the specified proxy definition to the selector. Proxy definitions are ordered, the first matching proxy for
     * a given repository will be used.
     * 
     * @param proxy The proxy definition to add, must not be {@code null}.
     * @param nonProxyHosts The list of hosts to exclude from proxying, may be {@code null}.
     * @return This proxy selector for chaining, never {@code null}.
     */
    public DefaultProxySelector add( Proxy proxy, String nonProxyHosts )
    {
        proxies.add( new ProxyDef( proxy, nonProxyHosts ) );

        return this;
    }

    public Proxy getProxy( RemoteRepository repository )
    {
        Map<String, ProxyDef> candidates = new HashMap<String, ProxyDef>();

        String host = repository.getHost();
        for ( ProxyDef proxy : proxies )
        {
            if ( !isNonProxyHosts( host, proxy.nonProxyHosts ) )
            {
                String key = proxy.proxy.getType().toLowerCase( Locale.ENGLISH );
                if ( !candidates.containsKey( key ) )
                {
                    candidates.put( key, proxy );
                }
            }
        }

        String protocol = repository.getProtocol().toLowerCase( Locale.ENGLISH );

        if ( "davs".equals( protocol ) )
        {
            protocol = "https";
        }
        else if ( "dav".equals( protocol ) )
        {
            protocol = "http";
        }
        else if ( protocol.startsWith( "dav:" ) )
        {
            protocol = protocol.substring( "dav:".length() );
        }

        ProxyDef proxy = candidates.get( protocol );
        if ( proxy == null && "https".equals( protocol ) )
        {
            proxy = candidates.get( "http" );
        }

        return ( proxy != null ) ? proxy.proxy : null;
    }

    static boolean isNonProxyHosts( String host, String nonProxyHosts )
    {
        if ( host != null && nonProxyHosts != null && nonProxyHosts.length() > 0 )
        {
            for ( StringTokenizer tokenizer = new StringTokenizer( nonProxyHosts, "|" ); tokenizer.hasMoreTokens(); )
            {
                String pattern = tokenizer.nextToken();
                pattern = pattern.replace( ".", "\\." ).replace( "*", ".*" );
                if ( host.matches( pattern ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    static class ProxyDef
    {

        final Proxy proxy;

        final String nonProxyHosts;

        public ProxyDef( Proxy proxy, String nonProxyHosts )
        {
            this.proxy = proxy;
            this.nonProxyHosts = nonProxyHosts;
        }

    }

}
