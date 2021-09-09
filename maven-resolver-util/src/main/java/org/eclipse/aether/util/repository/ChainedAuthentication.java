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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

import static java.util.Objects.requireNonNull;

/**
 * Authentication that aggregates other authentication blocks. When multiple input authentication blocks provide the
 * same authentication key, the last written value wins.
 */
final class ChainedAuthentication
    implements Authentication
{

    private final Authentication[] authentications;

    ChainedAuthentication( Authentication... authentications )
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

    ChainedAuthentication( Collection<? extends Authentication> authentications )
    {
        if ( authentications != null && !authentications.isEmpty() )
        {
            this.authentications = authentications.toArray( new Authentication[0] );
        }
        else
        {
            this.authentications = new Authentication[0];
        }
    }

    public void fill( AuthenticationContext context, String key, Map<String, String> data )
    {
        requireNonNull( context, "context cannot be null" );
        for ( Authentication authentication : authentications )
        {
            authentication.fill( context, key, data );
        }
    }

    public void digest( AuthenticationDigest digest )
    {
        requireNonNull( digest, "digest cannot be null" );
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
