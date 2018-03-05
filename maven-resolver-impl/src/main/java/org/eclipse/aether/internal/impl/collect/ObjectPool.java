package org.eclipse.aether.internal.impl.collect;

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
