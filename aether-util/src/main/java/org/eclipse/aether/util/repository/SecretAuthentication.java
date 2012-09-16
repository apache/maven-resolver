/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository;

import java.util.Arrays;
import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

/**
 * Authentication block that manages a single authentication key and its secret string value (password, passphrase).
 * Unlike {@link StringAuthentication}, the string value is kept in an encrypted buffer and only decrypted when needed
 * to reduce the potential of leaking the secret in a heap dump.
 */
final class SecretAuthentication
    implements Authentication
{

    private static final Object[] KEYS;

    static
    {
        KEYS = new Object[8];
        for ( int i = 0; i < KEYS.length; i++ )
        {
            KEYS[i] = new Object();
        }
    }

    private final String key;

    private final char[] value;

    private final int secretHash;

    public SecretAuthentication( String key, String value )
    {
        this( ( value != null ) ? value.toCharArray() : null, key );
    }

    public SecretAuthentication( String key, char[] value )
    {
        this( copy( value ), key );
    }

    private SecretAuthentication( char[] value, String key )
    {
        if ( key == null )
        {
            throw new IllegalArgumentException( "authentication key missing" );
        }
        this.key = key;
        this.secretHash = Arrays.hashCode( value ) ^ KEYS[0].hashCode();
        this.value = xor( value );
    }

    private static char[] copy( char[] chars )
    {
        return ( chars != null ) ? chars.clone() : null;
    }

    private char[] xor( char[] chars )
    {
        if ( chars != null )
        {
            int mask = System.identityHashCode( this );
            for ( int i = 0; i < chars.length; i++ )
            {
                int key = KEYS[( i >> 1 ) % KEYS.length].hashCode();
                key ^= mask;
                chars[i] ^= ( ( i & 1 ) == 0 ) ? ( key & 0xFF ) : ( key >>> 16 );
            }
        }
        return chars;
    }

    private static void clear( char[] chars )
    {
        if ( chars != null )
        {
            for ( int i = 0; i < chars.length; i++ )
            {
                chars[i] = '\0';
            }
        }
    }

    public void fill( AuthenticationContext context, String key, Map<String, String> data )
    {
        char[] secret = copy( value );
        xor( secret );
        context.put( this.key, secret );
        // secret will be cleared upon AuthenticationContext.close()
    }

    public void digest( AuthenticationDigest digest )
    {
        char[] secret = copy( value );
        try
        {
            xor( secret );
            digest.update( key );
            digest.update( value );
        }
        finally
        {
            clear( secret );
        }
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
        SecretAuthentication that = (SecretAuthentication) obj;
        if ( !eq( key, that.key ) || secretHash != that.secretHash )
        {
            return false;
        }
        char[] secret = copy( value );
        char[] thatSecret = copy( that.value );
        try
        {
            xor( secret );
            that.xor( thatSecret );
            return Arrays.equals( secret, thatSecret );
        }
        finally
        {
            clear( secret );
            clear( thatSecret );
        }
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + key.hashCode();
        hash = hash * 31 + secretHash;
        return hash;
    }

    @Override
    public String toString()
    {
        return key + "=" + ( ( value != null ) ? "***" : "null" );
    }

}
