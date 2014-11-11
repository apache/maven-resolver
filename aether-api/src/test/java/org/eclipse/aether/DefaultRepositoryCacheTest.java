/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether;

import static org.junit.Assert.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class DefaultRepositoryCacheTest
{

    private DefaultRepositoryCache cache = new DefaultRepositoryCache();

    private RepositorySystemSession session = new DefaultRepositorySystemSession();

    private Object get( Object key )
    {
        return cache.get( session, key );
    }

    private void put( Object key, Object value )
    {
        cache.put( session, key, value );
    }

    @Test( expected = RuntimeException.class )
    public void testGet_NullKey()
    {
        get( null );
    }

    @Test( expected = RuntimeException.class )
    public void testPut_NullKey()
    {
        put( null, "data" );
    }

    @Test
    public void testGetPut()
    {
        Object key = "key";
        assertNull( get( key ) );
        put( key, "value" );
        assertEquals( "value", get( key ) );
        put( key, "changed" );
        assertEquals( "changed", get( key ) );
        put( key, null );
        assertNull( get( key ) );
    }

    @Test( timeout = 10000 )
    public void testConcurrency()
        throws Exception
    {
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread threads[] = new Thread[20];
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
                            put( key, Boolean.TRUE );
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
