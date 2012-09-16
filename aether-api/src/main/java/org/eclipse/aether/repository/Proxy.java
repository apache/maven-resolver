/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.repository;

/**
 * A proxy to use for connections to a repository.
 */
public final class Proxy
{

    /**
     * Type denoting a proxy for HTTP transfers.
     */
    public static final String TYPE_HTTP = "http";

    /**
     * Type denoting a proxy for HTTPS transfers.
     */
    public static final String TYPE_HTTPS = "https";

    private final String type;

    private final String host;

    private final int port;

    private final Authentication auth;

    /**
     * Creates a new proxy with the specified properties.
     * 
     * @param type The type of the proxy, e.g. "http", may be {@code null}.
     * @param host The host of the proxy, may be {@code null}.
     * @param port The port of the proxy.
     * @param auth The authentication to use for the proxy connection, may be {@code null}.
     */
    public Proxy( String type, String host, int port, Authentication auth )
    {
        this.type = ( type != null ) ? type : "";
        this.host = ( host != null ) ? host : "";
        this.port = port;
        this.auth = auth;
    }

    /**
     * Gets the type of this proxy.
     * 
     * @return The type of this proxy, never {@code null}.
     */
    public String getType()
    {
        return type;
    }

    /**
     * Gets the host for this proxy.
     * 
     * @return The host for this proxy, never {@code null}.
     */
    public String getHost()
    {
        return host;
    }

    /**
     * Gets the port number for this proxy.
     * 
     * @return The port number for this proxy.
     */
    public int getPort()
    {
        return port;
    }

    /**
     * Gets the authentication to use for the proxy connection.
     * 
     * @return The authentication to use or {@code null} if none.
     */
    public Authentication getAuthentication()
    {
        return auth;
    }

    @Override
    public String toString()
    {
        return getHost() + ':' + getPort();
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        Proxy that = (Proxy) obj;

        return eq( type, that.type ) && eq( host, that.host ) && port == that.port && eq( auth, that.auth );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( host );
        hash = hash * 31 + hash( type );
        hash = hash * 31 + port;
        hash = hash * 31 + hash( auth );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
