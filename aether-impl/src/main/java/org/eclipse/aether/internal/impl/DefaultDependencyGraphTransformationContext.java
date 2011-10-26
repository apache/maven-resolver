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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;

/**
 */
class DefaultDependencyGraphTransformationContext
    implements DependencyGraphTransformationContext
{

    private final RepositorySystemSession session;

    private final Map<Object, Object> map;

    public DefaultDependencyGraphTransformationContext( RepositorySystemSession session )
    {
        this.session = session;
        this.map = new HashMap<Object, Object>();
    }

    public RepositorySystemSession getSession()
    {
        return session;
    }

    public Object get( Object key )
    {
        return map.get( key );
    }

    public Object put( Object key, Object value )
    {
        return map.put( key, value );
    }

    @Override
    public String toString()
    {
        return String.valueOf( map );
    }

}
