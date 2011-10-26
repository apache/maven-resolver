/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;

/**
 * A simplistic repository cache backed by a {@link ConcurrentHashMap}. The simplistic nature of this cache makes it
 * only suitable for use with short-lived repository system sessions.
 */
public final class DefaultRepositoryCache
    implements RepositoryCache
{

    private Map<Object, Object> cache = new ConcurrentHashMap<Object, Object>( 256 );

    public Object get( RepositorySystemSession session, Object key )
    {
        return cache.get( key );
    }

    public void put( RepositorySystemSession session, Object key, Object data )
    {
        if ( data != null )
        {
            cache.put( key, data );
        }
    }

}
