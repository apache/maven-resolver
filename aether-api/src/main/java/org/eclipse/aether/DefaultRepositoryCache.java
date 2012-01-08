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
package org.eclipse.aether;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simplistic repository cache backed by a thread-safe map. The simplistic nature of this cache makes it only suitable
 * for use with short-lived repository system sessions where pruning of cache data is not required.
 */
public final class DefaultRepositoryCache
    implements RepositoryCache
{

    private final Map<Object, Object> cache = new ConcurrentHashMap<Object, Object>( 256 );

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
