package org.eclipse.aether;

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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A simple session data storage backed by a thread-safe map.
 */
public final class DefaultSessionData
    implements SessionData
{

    private final ConcurrentMap<Object, Object> data;

    public DefaultSessionData()
    {
        data = new ConcurrentHashMap<>();
    }

    public void set( Object key, Object value )
    {
        requireNonNull( key, "key cannot be null" );

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
        requireNonNull( key, "key cannot be null" );

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
            if ( oldValue == null )
            {
                return !data.containsKey( key );
            }
            return data.remove( key, oldValue );
        }
    }

    public Object get( Object key )
    {
        requireNonNull( key, "key cannot be null" );

        return data.get( key );
    }

}
