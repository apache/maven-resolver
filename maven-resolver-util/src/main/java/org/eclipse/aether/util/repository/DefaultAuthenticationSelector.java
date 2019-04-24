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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A simple authentication selector that selects authentication based on repository identifiers.
 */
public final class DefaultAuthenticationSelector
    implements AuthenticationSelector
{

    private final Map<String, Authentication> repos = new HashMap<>();

    /**
     * Adds the specified authentication info for the given repository identifier.
     * 
     * @param id The identifier of the repository to add the authentication for, must not be {@code null}.
     * @param auth The authentication to add, may be {@code null}.
     * @return This selector for chaining, never {@code null}.
     */
    public DefaultAuthenticationSelector add( String id, Authentication auth )
    {
        if ( auth != null )
        {
            repos.put( id, auth );
        }
        else
        {
            repos.remove( id );
        }

        return this;
    }

    public Authentication getAuthentication( RemoteRepository repository )
    {
        return repos.get( repository.getId() );
    }

}
