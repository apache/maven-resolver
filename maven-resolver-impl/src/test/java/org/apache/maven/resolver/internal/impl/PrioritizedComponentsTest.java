package org.apache.maven.resolver.internal.impl;

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.apache.maven.resolver.ConfigurationProperties;
import org.junit.Test;

public class PrioritizedComponentsTest
{

    @Test
    public void testGetConfigKeys()
    {
        String[] keys =
            { ConfigurationProperties.PREFIX_PRIORITY + "java.lang.String",
                ConfigurationProperties.PREFIX_PRIORITY + "String" };
        assertArrayEquals( keys, PrioritizedComponents.getConfigKeys( String.class ) );

        keys =
            new String[] { ConfigurationProperties.PREFIX_PRIORITY + "java.util.concurrent.ThreadFactory",
                ConfigurationProperties.PREFIX_PRIORITY + "ThreadFactory",
                ConfigurationProperties.PREFIX_PRIORITY + "Thread" };
        assertArrayEquals( keys, PrioritizedComponents.getConfigKeys( ThreadFactory.class ) );
    }

    @Test
    public void testAdd_PriorityOverride()
    {
        Exception comp1 = new IllegalArgumentException();
        Exception comp2 = new NullPointerException();
        Map<Object, Object> config = new HashMap<>();
        config.put( ConfigurationProperties.PREFIX_PRIORITY + comp1.getClass().getName(), 6 );
        config.put( ConfigurationProperties.PREFIX_PRIORITY + comp2.getClass().getName(), 7 );
        PrioritizedComponents<Exception> components = new PrioritizedComponents<>( config );
        components.add( comp1, 1 );
        components.add( comp2, 0 );
        List<PrioritizedComponent<Exception>> sorted = components.getEnabled();
        assertEquals( 2, sorted.size() );
        assertSame( comp2, sorted.get( 0 ).getComponent() );
        assertEquals( 7, sorted.get( 0 ).getPriority(), 0.1f );
        assertSame( comp1, sorted.get( 1 ).getComponent() );
        assertEquals( 6, sorted.get( 1 ).getPriority(), 0.1f );
    }

    @Test
    public void testAdd_ImplicitPriority()
    {
        Exception comp1 = new IllegalArgumentException();
        Exception comp2 = new NullPointerException();
        Map<Object, Object> config = new HashMap<>();
        config.put( ConfigurationProperties.IMPLICIT_PRIORITIES, true );
        PrioritizedComponents<Exception> components = new PrioritizedComponents<>( config );
        components.add( comp1, 1 );
        components.add( comp2, 2 );
        List<PrioritizedComponent<Exception>> sorted = components.getEnabled();
        assertEquals( 2, sorted.size() );
        assertSame( comp1, sorted.get( 0 ).getComponent() );
        assertSame( comp2, sorted.get( 1 ).getComponent() );
    }

    @Test
    public void testAdd_Disabled()
    {
        Exception comp1 = new IllegalArgumentException();
        Exception comp2 = new NullPointerException();
        Map<Object, Object> config = new HashMap<>();
        PrioritizedComponents<Exception> components = new PrioritizedComponents<>( config );

        components.add( new UnsupportedOperationException(), Float.NaN );
        List<PrioritizedComponent<Exception>> sorted = components.getEnabled();
        assertEquals( 0, sorted.size() );

        components.add( comp1, 1 );
        sorted = components.getEnabled();
        assertEquals( 1, sorted.size() );
        assertSame( comp1, sorted.get( 0 ).getComponent() );

        components.add( new Exception(), Float.NaN );
        sorted = components.getEnabled();
        assertEquals( 1, sorted.size() );
        assertSame( comp1, sorted.get( 0 ).getComponent() );

        components.add( comp2, 0 );
        sorted = components.getEnabled();
        assertEquals( 2, sorted.size() );
        assertSame( comp1, sorted.get( 0 ).getComponent() );
        assertSame( comp2, sorted.get( 1 ).getComponent() );
    }

    @Test
    public void testList()
    {
        Exception comp1 = new IllegalArgumentException();
        Exception comp2 = new NullPointerException();

        PrioritizedComponents<Exception> components = new PrioritizedComponents<>( Collections.emptyMap() );
        components.add( comp1, 1 );
        components.add( comp2, 0 );

        StringBuilder stringBuilder = new StringBuilder();
        components.list( stringBuilder );

        assertEquals( "IllegalArgumentException, NullPointerException", stringBuilder.toString() );
    }
}
