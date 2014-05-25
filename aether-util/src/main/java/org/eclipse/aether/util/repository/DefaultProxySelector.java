/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     * <p>
     * The syntax of the non-proxy host list matches that of the property "http.nonProxyHosts" from the JRE, i.e. the
     * asterisk character ('*') serves as a wildcard for pattern matching. Multiple non-proxy hosts are separated by the
     * pipe character ('|') but note that surrounding whitespace is not trimmed from the entries.
     * 
     * @param proxy The proxy definition to add, must not be {@code null}.
     * @param nonProxyHosts The list of (case-insensitive) host names to exclude from proxying, may be {@code null}.
     * @return This proxy selector for chaining, never {@code null}.
     */
    public DefaultProxySelector add( Proxy proxy, String nonProxyHosts )
    {
        return add( proxy, NonProxyHosts.split( nonProxyHosts ) );
    }

    /**
     * Adds the specified proxy definition to the selector. Proxy definitions are ordered, the first matching proxy for
     * a given repository will be used.
     * <p>
     * The asterisk character ('*') may be used as wildcard in a host name.
     * 
     * @param proxy The proxy definition to add, must not be {@code null}.
     * @param nonProxyHosts The list of (case-insensitive) host names to exclude from proxying, may be {@code null} but
     *            must not contain {@code null} elements.
     * @return This proxy selector for chaining, never {@code null}.
     * @since 1.1.0
     */
    public DefaultProxySelector add( Proxy proxy, Collection<String> nonProxyHosts )
    {
        if ( proxy == null )
        {
            throw new IllegalArgumentException( "proxy not specified" );
        }
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

        public NonProxyHosts( Collection<String> nonProxyHosts )
        {
            List<Pattern> patterns = new ArrayList<Pattern>();
            if ( nonProxyHosts != null )
            {
                for ( String nonProxyHost : nonProxyHosts )
                {
                    String pattern = nonProxyHost;
                    pattern = pattern.replace( ".", "\\." ).replace( "*", ".*" );
                    patterns.add( Pattern.compile( pattern, Pattern.CASE_INSENSITIVE ) );
                }
            }
            this.patterns = patterns.toArray( new Pattern[patterns.size()] );
        }

        static List<String> split( String nonProxyHosts )
        {
            List<String> hosts = null;
            if ( nonProxyHosts != null )
            {
                hosts = new ArrayList<String>();
                for ( StringTokenizer tokenizer = new StringTokenizer( nonProxyHosts, "|" ); tokenizer.hasMoreTokens(); )
                {
                    hosts.add( tokenizer.nextToken() );
                }
            }
            return hosts;
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

        public ProxyDef( Proxy proxy, Collection<String> nonProxyHosts )
        {
            this.proxy = proxy;
            this.nonProxyHosts = new NonProxyHosts( nonProxyHosts );
        }

    }

}
