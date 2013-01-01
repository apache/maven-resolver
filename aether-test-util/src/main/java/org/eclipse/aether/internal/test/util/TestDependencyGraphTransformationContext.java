/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;

/**
 */
class TestDependencyGraphTransformationContext
    implements DependencyGraphTransformationContext
{

    private final RepositorySystemSession session;

    private final Map<Object, Object> map;

    public TestDependencyGraphTransformationContext( RepositorySystemSession session )
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
        if ( key == null )
        {
            throw new IllegalArgumentException( "key must not be null" );
        }
        return map.get( key );
    }

    public Object put( Object key, Object value )
    {
        if ( key == null )
        {
            throw new IllegalArgumentException( "key must not be null" );
        }
        if ( value != null )
        {
            return map.put( key, value );
        }
        else
        {
            return map.remove( key );
        }
    }

    @Override
    public String toString()
    {
        return String.valueOf( map );
    }

}
