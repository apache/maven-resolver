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

import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.util.ConfigUtils;

/**
 * SSL-related configuration and cache key for connection pools (whose scheme registries are derived from this config).
 */
final class SslConfig
{

    private static final String CIPHER_SUITES = "https.cipherSuites";

    private static final String PROTOCOLS = "https.protocols";

    final SSLContext context;

    final HostnameVerifier verifier;

    final String[] cipherSuites;

    final String[] protocols;

    public SslConfig( RepositorySystemSession session, AuthenticationContext authContext )
    {
        context =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_CONTEXT, SSLContext.class ) : null;
        verifier =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_HOSTNAME_VERIFIER,
                                                       HostnameVerifier.class ) : null;

        cipherSuites = split( get( session, CIPHER_SUITES ) );
        protocols = split( get( session, PROTOCOLS ) );
    }

    private static String get( RepositorySystemSession session, String key )
    {
        String value = ConfigUtils.getString( session, null, "aether.connector." + key, key );
        if ( value == null )
        {
            value = System.getProperty( key );
        }
        return value;
    }

    private static String[] split( String value )
    {
        if ( value == null || value.length() <= 0 )
        {
            return null;
        }
        return value.split( ",+" );
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
        SslConfig that = (SslConfig) obj;
        return eq( context, that.context ) && eq( verifier, that.verifier )
            && Arrays.equals( cipherSuites, that.cipherSuites ) && Arrays.equals( protocols, that.protocols );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( context );
        hash = hash * 31 + hash( verifier );
        hash = hash * 31 + Arrays.hashCode( cipherSuites );
        hash = hash * 31 + Arrays.hashCode( protocols );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
