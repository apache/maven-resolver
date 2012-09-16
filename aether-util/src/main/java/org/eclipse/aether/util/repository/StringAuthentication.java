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

import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

/**
 * Authentication block that manages a single authentication key and its string value.
 */
final class StringAuthentication
    implements Authentication
{

    private final String key;

    private final String value;

    public StringAuthentication( String key, String value )
    {
        if ( key == null )
        {
            throw new IllegalArgumentException( "authentication key missing" );
        }
        this.key = key;
        this.value = value;
    }

    public void fill( AuthenticationContext context, String key, Map<String, String> data )
    {
        context.put( this.key, value );
    }

    public void digest( AuthenticationDigest digest )
    {
        digest.update( key, value );
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
        StringAuthentication that = (StringAuthentication) obj;
        return eq( key, that.key ) && eq( value, that.value );
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
        hash = hash * 31 + ( ( value != null ) ? value.hashCode() : 0 );
        return hash;
    }

    @Override
    public String toString()
    {
        return key + "=" + value;
    }

}
