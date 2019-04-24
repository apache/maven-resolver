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

import static org.junit.Assert.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class DefaultSessionDataTest
{

    private DefaultSessionData data = new DefaultSessionData();

    private Object get( Object key )
    {
        return data.get( key );
    }

    private void set( Object key, Object value )
    {
        data.set( key, value );
    }

    private boolean set( Object key, Object oldValue, Object newValue )
    {
        return data.set( key, oldValue, newValue );
    }

    @Test( expected = RuntimeException.class )
    public void testGet_NullKey()
    {
        get( null );
    }

    @Test( expected = RuntimeException.class )
    public void testSet_NullKey()
    {
        set( null, "data" );
    }

    @Test
    public void testGetSet()
    {
        Object key = "key";
        assertNull( get( key ) );
        set( key, "value" );
        assertEquals( "value", get( key ) );
        set( key, "changed" );
        assertEquals( "changed", get( key ) );
        set( key, null );
        assertNull( get( key ) );
    }

    @Test
    public void testGetSafeSet()
    {
        Object key = "key";
        assertNull( get( key ) );
        assertFalse( set( key, "wrong", "value" ) );
        assertNull( get( key ) );
        assertTrue( set( key, null, "value" ) );
        assertEquals( "value", get( key ) );
        assertTrue( set( key, "value", "value" ) );
        assertEquals( "value", get( key ) );
        assertFalse( set( key, "wrong", "changed" ) );
        assertEquals( "value", get( key ) );
        assertTrue( set( key, "value", "changed" ) );
        assertEquals( "changed", get( key ) );
        assertFalse( set( key, "wrong", null ) );
        assertEquals( "changed", get( key ) );
        assertTrue( set( key, "changed", null ) );
        assertNull( get( key ) );
        assertTrue( set( key, null, null ) );
        assertNull( get( key ) );
    }

    @Test( timeout = 10000L )
    public void testConcurrency()
        throws Exception
    {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        Thread[] threads = new Thread[20];
        for ( int i = 0; i < threads.length; i++ )
        {
            threads[i] = new Thread()
            {
                @Override
                public void run()
                {
                    for ( int i = 0; i < 100; i++ )
                    {
                        String key = UUID.randomUUID().toString();
                        try
                        {
                            set( key, Boolean.TRUE );
                            assertEquals( Boolean.TRUE, get( key ) );
                        }
                        catch ( Throwable t )
                        {
                            error.compareAndSet( null, t );
                            t.printStackTrace();
                        }
                    }
                }
            };
        }
        for ( Thread thread : threads )
        {
            thread.start();
        }
        for ( Thread thread : threads )
        {
            thread.join();
        }
        assertNull( String.valueOf( error.get() ), error.get() );
    }
}
