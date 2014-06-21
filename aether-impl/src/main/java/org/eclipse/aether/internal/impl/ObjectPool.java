/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
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

import org.eclipse.aether.graph.Dependency;

/**
 * Pool of immutable object instances, used to avoid excessive memory consumption of (dirty) dependency graph which
 * tends to have many duplicate artifacts/dependencies.
 */
final class ObjectPool
{

    private final Map<Object, Reference<Dependency>> dependencies =
        new WeakHashMap<Object, Reference<Dependency>>( 256 );

    public synchronized Dependency intern( Dependency dependency )
    {
        Reference<Dependency> pooledRef = dependencies.get( dependency );
        if ( pooledRef != null )
        {
            Dependency pooled = pooledRef.get();
            if ( pooled != null )
            {
                return pooled;
            }
        }
        dependencies.put( dependency, new WeakReference<Dependency>( dependency ) );
        return dependency;
    }

}
