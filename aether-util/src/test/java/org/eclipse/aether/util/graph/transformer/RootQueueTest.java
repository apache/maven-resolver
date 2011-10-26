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
package org.eclipse.aether.util.graph.transformer;

import static org.junit.Assert.*;

import org.eclipse.aether.util.graph.transformer.ConflictIdSorter.ConflictId;
import org.eclipse.aether.util.graph.transformer.ConflictIdSorter.RootQueue;
import org.junit.Test;

public class RootQueueTest
{

    @Test
    public void testIsEmpty()
    {
        ConflictId id = new ConflictId( "a", 0 );
        RootQueue queue = new RootQueue( 10 );
        assertTrue( queue.isEmpty() );
        queue.add( id );
        assertFalse( queue.isEmpty() );
        assertSame( id, queue.remove() );
        assertTrue( queue.isEmpty() );
    }

    @Test
    public void testAddSortsByDepth()
    {
        ConflictId id1 = new ConflictId( "a", 0 );
        ConflictId id2 = new ConflictId( "b", 1 );
        ConflictId id3 = new ConflictId( "c", 2 );
        ConflictId id4 = new ConflictId( "d", 3 );

        RootQueue queue = new RootQueue( 10 );
        queue.add( id1 );
        queue.add( id2 );
        queue.add( id3 );
        queue.add( id4 );
        assertSame( id1, queue.remove() );
        assertSame( id2, queue.remove() );
        assertSame( id3, queue.remove() );
        assertSame( id4, queue.remove() );

        queue = new RootQueue( 10 );
        queue.add( id4 );
        queue.add( id3 );
        queue.add( id2 );
        queue.add( id1 );
        assertSame( id1, queue.remove() );
        assertSame( id2, queue.remove() );
        assertSame( id3, queue.remove() );
        assertSame( id4, queue.remove() );
    }

    @Test
    public void testAddWithArrayCompact()
    {
        ConflictId id = new ConflictId( "a", 0 );

        RootQueue queue = new RootQueue( 10 );
        assertTrue( queue.isEmpty() );
        queue.add( id );
        assertFalse( queue.isEmpty() );
        assertSame( id, queue.remove() );
        assertTrue( queue.isEmpty() );
        queue.add( id );
        assertFalse( queue.isEmpty() );
        assertSame( id, queue.remove() );
        assertTrue( queue.isEmpty() );
    }

    @Test
    public void testAddMinimumAfterSomeRemoves()
    {
        ConflictId id1 = new ConflictId( "a", 0 );
        ConflictId id2 = new ConflictId( "b", 1 );
        ConflictId id3 = new ConflictId( "c", 2 );

        RootQueue queue = new RootQueue( 10 );
        queue.add( id2 );
        queue.add( id3 );
        assertSame( id2, queue.remove() );
        queue.add( id1 );
        assertSame( id1, queue.remove() );
        assertSame( id3, queue.remove() );
        assertTrue( queue.isEmpty() );
    }

}
