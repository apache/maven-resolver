/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import org.eclipse.aether.ConfigurationProperties;
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
        Map<Object, Object> config = new HashMap<Object, Object>();
        config.put( ConfigurationProperties.PREFIX_PRIORITY + comp1.getClass().getName(), 6 );
        config.put( ConfigurationProperties.PREFIX_PRIORITY + comp2.getClass().getName(), 7 );
        PrioritizedComponents<Exception> components = new PrioritizedComponents<Exception>( config );
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
        Map<Object, Object> config = new HashMap<Object, Object>();
        config.put( ConfigurationProperties.IMPLICIT_PRIORITIES, true );
        PrioritizedComponents<Exception> components = new PrioritizedComponents<Exception>( config );
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
        Map<Object, Object> config = new HashMap<Object, Object>();
        PrioritizedComponents<Exception> components = new PrioritizedComponents<Exception>( config );

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
}
