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
package org.eclipse.aether.util.repository;

import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

/**
 * Authentication block that manages a single authentication key and its component value. In this context, component
 * refers to an object whose behavior is solely dependent on its implementation class.
 */
final class ComponentAuthentication
    implements Authentication
{

    private final String key;

    private final Object value;

    public ComponentAuthentication( String key, Object value )
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
        if ( value != null )
        {
            digest.update( key, value.getClass().getName() );
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
        ComponentAuthentication that = (ComponentAuthentication) obj;
        return key.equals( that.key ) && eqClass( value, that.value );
    }

    private static <T> boolean eqClass( T s1, T s2 )
    {
        return ( s1 == null ) ? s2 == null : s2 != null && s1.getClass().equals( s2.getClass() );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + key.hashCode();
        hash = hash * 31 + ( ( value != null ) ? value.getClass().hashCode() : 0 );
        return hash;
    }

    @Override
    public String toString()
    {
        return key + "=" + value;
    }

}
