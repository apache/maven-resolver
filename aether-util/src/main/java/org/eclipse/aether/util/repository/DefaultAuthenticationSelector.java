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
package org.eclipse.aether.util.repository;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;

/**
 * A simple authentication selector that selects authentication based on repository identifiers.
 */
public final class DefaultAuthenticationSelector
    implements AuthenticationSelector
{

    private final Map<String, Authentication> repos = new HashMap<String, Authentication>();

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
