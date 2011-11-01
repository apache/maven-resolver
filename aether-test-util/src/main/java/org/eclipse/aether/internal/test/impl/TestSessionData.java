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
package org.eclipse.aether.internal.test.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.aether.SessionData;

/**
 * A simple session data storage backed by a {@link ConcurrentHashMap}.
 */
class TestSessionData
    implements SessionData
{

    private ConcurrentMap<Object, Object> data;

    public TestSessionData()
    {
        data = new ConcurrentHashMap<Object, Object>();
    }

    public void set( Object key, Object value )
    {
        if ( value != null )
        {
            data.put( key, value );
        }
        else
        {
            data.remove( key );
        }
    }

    public boolean set( Object key, Object oldValue, Object newValue )
    {
        if ( key == null )
        {
            throw new IllegalArgumentException( "key must not be null" );
        }

        if ( newValue != null )
        {
            if ( oldValue == null )
            {
                return data.putIfAbsent( key, newValue ) == null;
            }
            return data.replace( key, oldValue, newValue );
        }
        else
        {
            return data.remove( key, oldValue );
        }
    }

    public Object get( Object key )
    {
        return data.get( key );
    }

}
