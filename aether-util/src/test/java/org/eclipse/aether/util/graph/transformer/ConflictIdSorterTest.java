package org.eclipse.aether.util.graph.transformer;

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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.transformer.ConflictIdSorter;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.junit.Test;

/**
 */
public class ConflictIdSorterTest
    extends AbstractDependencyGraphTransformerTest
{

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new ChainedDependencyGraphTransformer( new SimpleConflictMarker(), new ConflictIdSorter() );
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/conflict-id-sorter/" );
    }

    private void expectOrder( List<String> sorted, String... ids )
    {
        Queue<String> queue = new LinkedList<String>( sorted );

        for ( String id : ids )
        {
            String item = queue.poll();
            assertNotNull( String.format( "not enough conflict groups (no match for '%s'", id ), item );

            if ( !"*".equals( id ) )
            {
                assertEquals( id, item );
            }
        }

        assertTrue( String.format( "leftover conflict groups (remaining: '%s')", queue ), queue.isEmpty() );
    }

    private void expectOrder( String... id )
    {
        @SuppressWarnings( "unchecked" )
        List<String> sorted = (List<String>) context.get( TransformationContextKeys.SORTED_CONFLICT_IDS );
        expectOrder( sorted, id );
    }

    private void expectCycle( boolean cycle )
    {
        Collection<?> cycles = (Collection<?>) context.get( TransformationContextKeys.CYCLIC_CONFLICT_IDS );
        assertEquals( cycle, !cycles.isEmpty() );
    }

    @Test
    public void testSimple()
        throws Exception
    {
        DependencyNode node = parseResource( "simple.txt" );
        assertSame( node, transform( node ) );

        expectOrder( "gid2:aid::jar", "gid:aid::jar", "gid:aid2::jar" );
        expectCycle( false );
    }

    @Test
    public void testCycle()
        throws Exception
    {
        DependencyNode node = parseResource( "cycle.txt" );
        assertSame( node, transform( node ) );

        expectOrder( "gid:aid::jar", "gid2:aid::jar" );
        expectCycle( true );
    }

    @Test
    public void testCycles()
        throws Exception
    {
        DependencyNode node = parseResource( "cycles.txt" );
        assertSame( node, transform( node ) );

        expectOrder( "*", "*", "*", "gid:aid::jar" );
        expectCycle( true );
    }

    @Test
    public void testNoConflicts()
        throws Exception
    {
        DependencyNode node = parseResource( "no-conflicts.txt" );
        assertSame( node, transform( node ) );

        expectOrder( "gid:aid::jar", "gid3:aid::jar", "gid2:aid::jar", "gid4:aid::jar" );
        expectCycle( false );
    }

}
