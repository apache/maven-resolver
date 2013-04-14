/*******************************************************************************
 * Copyright (c) 2012, 2013 Sonatype, Inc.
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
import java.util.Collection;
import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

/**
 * Authentication that aggregates other authentication blocks. When multiple input authentication blocks provide the
 * same authentication key, the last written value wins.
 */
final class ChainedAuthentication
    implements Authentication
{

    private final Authentication[] authentications;

    public ChainedAuthentication( Authentication... authentications )
    {
        if ( authentications != null && authentications.length > 0 )
        {
            this.authentications = authentications.clone();
        }
        else
        {
            this.authentications = new Authentication[0];
        }
    }

    public ChainedAuthentication( Collection<? extends Authentication> authentications )
    {
        if ( authentications != null && !authentications.isEmpty() )
        {
            this.authentications = authentications.toArray( new Authentication[authentications.size()] );
        }
        else
        {
            this.authentications = new Authentication[0];
        }
    }

    public void fill( AuthenticationContext context, String key, Map<String, String> data )
    {
        for ( Authentication authentication : authentications )
        {
            authentication.fill( context, key, data );
        }
    }

    public void digest( AuthenticationDigest digest )
    {
        for ( Authentication authentication : authentications )
        {
            authentication.digest( digest );
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
        ChainedAuthentication that = (ChainedAuthentication) obj;
        return Arrays.equals( authentications, that.authentications );
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode( authentications );
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        for ( Authentication authentication : authentications )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( ", " );
            }
            buffer.append( authentication );
        }
        return buffer.toString();
    }

}
