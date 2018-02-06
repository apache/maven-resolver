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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;

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
     * @param nonProxyHosts The list of (case-insensitive) host names to exclude from proxying, may be {@code null}.
     * @return This proxy selector for chaining, never {@code null}.
     */
    public DefaultProxySelector add( Proxy proxy, String nonProxyHosts )
    {
        requireNonNull( proxy, "proxy cannot be null" );
        proxies.add( new ProxyDef( proxy, nonProxyHosts ) );

        return this;
    }

    public Proxy getProxy( RemoteRepository repository )
    {
        Map<String, ProxyDef> candidates = new HashMap<String, ProxyDef>();

        String host = repository.getHost();
        for ( ProxyDef proxy : proxies )
        {
            if ( !proxy.nonProxyHosts.isNonProxyHost( host ) )
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

    static class NonProxyHosts
    {

        private final Pattern[] patterns;

        NonProxyHosts( String nonProxyHosts )
        {
            List<Pattern> patterns = new ArrayList<Pattern>();
            if ( nonProxyHosts != null )
            {
                for ( StringTokenizer tokenizer = new StringTokenizer( nonProxyHosts, "|" ); tokenizer.hasMoreTokens(); )
                {
                    String pattern = tokenizer.nextToken();
                    pattern = pattern.replace( ".", "\\." ).replace( "*", ".*" );
                    patterns.add( Pattern.compile( pattern, Pattern.CASE_INSENSITIVE ) );
                }
            }
            this.patterns = patterns.toArray( new Pattern[patterns.size()] );
        }

        boolean isNonProxyHost( String host )
        {
            if ( host != null )
            {
                for ( Pattern pattern : patterns )
                {
                    if ( pattern.matcher( host ).matches() )
                    {
                        return true;
                    }
                }
            }
            return false;
        }

    }

    static class ProxyDef
    {

        final Proxy proxy;

        final NonProxyHosts nonProxyHosts;

        ProxyDef( Proxy proxy, String nonProxyHosts )
        {
            this.proxy = proxy;
            this.nonProxyHosts = new NonProxyHosts( nonProxyHosts );
        }

    }

}
