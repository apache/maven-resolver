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
package org.eclipse.aether.internal.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pool of immutable object instances, used to avoid excessive memory consumption of (dirty) dependency graph which
 * tends to have many duplicate artifacts/dependencies.
 */
class ObjectPool<T>
{

    private final Map<Object, Reference<T>> objects = new WeakHashMap<Object, Reference<T>>( 256 );

    public synchronized T intern( T object )
    {
        Reference<T> pooledRef = objects.get( object );
        if ( pooledRef != null )
        {
            T pooled = pooledRef.get();
            if ( pooled != null )
            {
                return pooled;
            }
        }

        objects.put( object, new WeakReference<T>( object ) );
        return object;
    }

}
