/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
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

import java.util.List;

import org.eclipse.aether.collection.UnsolvableVersionConflictException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

/**
 */
public class NearestVersionSelectorTest
    extends AbstractDependencyGraphTransformerTest
{

    private ConflictResolver newConflictResolver()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(), new JavaScopeDeriver() );
    }

    @Test
    public void testSelectHighestVersionFromMultipleVersionsAtSameLevel()
        throws Exception
    {
        // root
        // +- a:1
        // +- a:3
        // \- a:2

        DependencyNode a1 = builder.artifactId( "a" ).version( "1" ).build();
        DependencyNode a2 = builder.artifactId( "a" ).version( "2" ).build();
        DependencyNode a3 = builder.artifactId( "a" ).version( "3" ).build();

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a1 );
        root.getChildren().add( a3 );
        root.getChildren().add( a2 );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 1, root.getChildren().size() );
        assertSame( a3, root.getChildren().iterator().next() );
    }

    @Test
    public void testSelectedVersionAtDeeperLevelThanOriginallySeen()
        throws Exception
    {
        // root
        // +- a
        // |  \- b:1           # will be removed in favor of b:2
        // |     \- j:1        # nearest version of j in dirty tree
        // +- c
        // |  \- d
        // |     \- e
        // |        \- j:1
        // \- b:2

        DependencyNode j = builder.artifactId( "j" ).build();

        DependencyNode b1 = builder.artifactId( "b" ).version( "1" ).build();
        b1.getChildren().add( j );
        DependencyNode a = builder.artifactId( "a" ).build();
        a.getChildren().add( b1 );

        DependencyNode e = builder.artifactId( "e" ).build();
        e.getChildren().add( j );
        DependencyNode d = builder.artifactId( "d" ).build();
        d.getChildren().add( e );
        DependencyNode c = builder.artifactId( "c" ).build();
        c.getChildren().add( d );

        DependencyNode b2 = builder.artifactId( "b" ).version( "2" ).build();

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a );
        root.getChildren().add( c );
        root.getChildren().add( b2 );

        root = newConflictResolver().transformGraph( root, context );

        List<DependencyNode> trail = find( root, "j" );
        assertEquals( 5, trail.size() );
    }

    @Test
    public void testNearestDirtyVersionUnderneathRemovedNode()
        throws Exception
    {
        // root
        // +- a
        // |  \- b:1           # will be removed in favor of b:2
        // |     \- j:1        # nearest version of j in dirty tree
        // +- c
        // |  \- d
        // |     \- e
        // |        \- j:2
        // \- b:2

        DependencyNode j1 = builder.artifactId( "j" ).version( "1" ).build();
        DependencyNode j2 = builder.artifactId( "j" ).version( "2" ).build();

        DependencyNode b1 = builder.artifactId( "b" ).version( "1" ).build();
        b1.getChildren().add( j1 );
        DependencyNode a = builder.artifactId( "a" ).build();
        a.getChildren().add( b1 );

        DependencyNode e = builder.artifactId( "e" ).build();
        e.getChildren().add( j2 );
        DependencyNode d = builder.artifactId( "d" ).build();
        d.getChildren().add( e );
        DependencyNode c = builder.artifactId( "c" ).build();
        c.getChildren().add( d );

        DependencyNode b2 = builder.artifactId( "b" ).version( "2" ).build();

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a );
        root.getChildren().add( c );
        root.getChildren().add( b2 );

        root = newConflictResolver().transformGraph( root, context );

        List<DependencyNode> trail = find( root, "j" );
        assertEquals( 5, trail.size() );
    }

    @Test
    public void testViolationOfHardConstraintFallsBackToNearestSeenNotFirstSeen()
        throws Exception
    {
        // root
        // +- x:1
        // +- a:1
        // |  \- b:1
        // |     \- x:3
        // +- c:1
        // |  \- x:2
        // \- d:1
        //    \- e:1
        //       \- x:[2,)   # forces rejection of x:1, should fallback to nearest and not first-seen, i.e. x:2 and not x:3

        DependencyNode x1 = builder.artifactId( "x" ).version( "1" ).build();
        DependencyNode x2 = builder.artifactId( "x" ).version( "2" ).build();
        DependencyNode x3 = builder.artifactId( "x" ).version( "3" ).build();
        DependencyNode x2r = builder.artifactId( "x" ).version( "2" ).range( "[2,)" ).build();

        DependencyNode b = builder.artifactId( "b" ).version( "1" ).build();
        b.getChildren().add( x3 );
        DependencyNode a = builder.artifactId( "a" ).build();
        a.getChildren().add( b );

        DependencyNode c = builder.artifactId( "c" ).build();
        c.getChildren().add( x2 );

        DependencyNode e = builder.artifactId( "e" ).build();
        e.getChildren().add( x2r );
        DependencyNode d = builder.artifactId( "d" ).build();
        d.getChildren().add( e );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( x1 );
        root.getChildren().add( a );
        root.getChildren().add( c );
        root.getChildren().add( d );

        root = newConflictResolver().transformGraph( root, context );

        List<DependencyNode> trail = find( root, "x" );
        assertEquals( 3, trail.size() );
        assertSame( x2, trail.get( 0 ) );
    }

    @Test
    public void testCyclicConflictIdGraph()
        throws Exception
    {
        // root
        // +- a:1
        // |  \- b:1
        // \- b:2
        //    \- a:2

        DependencyNode a1 = builder.artifactId( "a" ).version( "1" ).build();
        DependencyNode a2 = builder.artifactId( "a" ).version( "2" ).build();

        DependencyNode b1 = builder.artifactId( "b" ).version( "1" ).build();
        DependencyNode b2 = builder.artifactId( "b" ).version( "2" ).build();

        a1.getChildren().add( b1 );
        b2.getChildren().add( a2 );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a1 );
        root.getChildren().add( b2 );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
        assertSame( a1, root.getChildren().get( 0 ) );
        assertSame( b2, root.getChildren().get( 1 ) );
        assertTrue( a1.getChildren().isEmpty() );
        assertTrue( b2.getChildren().isEmpty() );
    }

    @Test( expected = UnsolvableVersionConflictException.class )
    public void testUnsolvableRangeConflictBetweenHardConstraints()
        throws Exception
    {
        // root
        // +- b:1
        // |  \- a:[1]
        // \- c:1
        //    \- a:[2]

        DependencyNode a1 = builder.artifactId( "a" ).version( "1" ).range( "[1]" ).build();
        DependencyNode a2 = builder.artifactId( "a" ).version( "2" ).range( "[2]" ).build();

        DependencyNode b = builder.artifactId( "b" ).version( "1" ).build();
        DependencyNode c = builder.artifactId( "c" ).version( "1" ).build();

        b.getChildren().add( a1 );
        c.getChildren().add( a2 );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( b );
        root.getChildren().add( c );

        root = newConflictResolver().transformGraph( root, context );
    }

    @Test
    public void testSolvableConflictBetweenHardConstraints()
        throws Exception
    {
        // root
        // +- b:1
        // |  \- a:[2]
        // \- c:1
        //    +- a:1-[1,3]
        //    +- a:2-[1,3]
        //    \- a:3-[1,3]

        DependencyNode a1 = builder.artifactId( "a" ).version( "2" ).range( "[2]" ).build();
        DependencyNode a2 = builder.artifactId( "a" ).version( "1" ).range( "[1,3]" ).build();
        DependencyNode a3 = builder.artifactId( "a" ).version( "2" ).range( "[1,3]" ).build();
        DependencyNode a4 = builder.artifactId( "a" ).version( "3" ).range( "[1,3]" ).build();

        DependencyNode b = builder.artifactId( "b" ).version( "1" ).build();
        DependencyNode c = builder.artifactId( "c" ).version( "1" ).build();

        b.getChildren().add( a1 );
        c.getChildren().add( a2 );
        c.getChildren().add( a3 );
        c.getChildren().add( a4 );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( b );
        root.getChildren().add( c );

        root = newConflictResolver().transformGraph( root, context );
    }

    @Test
    public void testConflictGroupCompletelyDroppedFromResolvedTree()
        throws Exception
    {
        // root
        // +- a:1
        // |  \- b:1
        // |     \- c:1     # conflict group c will completely vanish from resolved tree
        // \- b:2

        DependencyNode a = builder.artifactId( "a" ).version( "1" ).build();
        DependencyNode b1 = builder.artifactId( "b" ).version( "1" ).build();
        DependencyNode b2 = builder.artifactId( "b" ).version( "2" ).build();
        DependencyNode c = builder.artifactId( "c" ).version( "1" ).build();

        b1.getChildren().add( c );
        a.getChildren().add( b1 );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a );
        root.getChildren().add( b2 );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
        assertSame( a, root.getChildren().get( 0 ) );
        assertSame( b2, root.getChildren().get( 1 ) );
        assertTrue( a.getChildren().isEmpty() );
        assertTrue( b2.getChildren().isEmpty() );
    }

    @Test
    public void testNearestSoftVersionPrunedByFartherRange()
        throws Exception
    {
        // root
        // +- a:1
        // |  \- c:2
        // \- b:1
        //    \- c:[1]

        DependencyNode a = builder.artifactId( "a" ).version( "1" ).build();
        DependencyNode b = builder.artifactId( "b" ).version( "1" ).build();
        DependencyNode c1 = builder.artifactId( "c" ).version( "1" ).range( "[1]" ).build();
        DependencyNode c2 = builder.artifactId( "c" ).version( "2" ).build();

        a.getChildren().add( c2 );
        b.getChildren().add( c1 );

        DependencyNode root = builder.artifactId( null ).build();
        root.getChildren().add( a );
        root.getChildren().add( b );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
        assertSame( a, root.getChildren().get( 0 ) );
        assertSame( b, root.getChildren().get( 1 ) );
        assertTrue( a.getChildren().isEmpty() );
        assertEquals( 1, b.getChildren().size() );
    }

    @Test
    public void testCyclicGraph()
        throws Exception
    {
        DependencyNode root = new DependencyGraphParser( "transformer/version-resolver/" ).parse( "cycle.txt" );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( 1, root.getChildren().get( 0 ).getChildren().size() );
        assertEquals( 0, root.getChildren().get( 0 ).getChildren().get( 0 ).getChildren().size() );
        assertEquals( 0, root.getChildren().get( 1 ).getChildren().size() );
    }

    @Test
    public void testLoop()
        throws Exception
    {
        DependencyNode root = new DependencyGraphParser( "transformer/version-resolver/" ).parse( "loop.txt" );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 0, root.getChildren().size() );
    }

    @Test
    public void testOverlappingCycles()
        throws Exception
    {
        DependencyNode root =
            new DependencyGraphParser( "transformer/version-resolver/" ).parse( "overlapping-cycles.txt" );

        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
    }

    @Test
    public void testScopeDerivationAndConflictResolutionCantHappenForAllNodesBeforeVersionSelection()
        throws Exception
    {
        DependencyNode root =
            new DependencyGraphParser( "transformer/version-resolver/" ).parse( "scope-vs-version.txt" );

        root = newConflictResolver().transformGraph( root, context );

        DependencyNode[] nodes = find( root, "y" ).toArray( new DependencyNode[0] );
        assertEquals( 3, nodes.length );
        assertEquals( "test", nodes[1].getDependency().getScope() );
        assertEquals( "test", nodes[0].getDependency().getScope() );
    }

    @Test
    public void testVerboseMode()
        throws Exception
    {
        DependencyNode root = new DependencyGraphParser( "transformer/version-resolver/" ).parse( "verbose.txt" );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.TRUE );
        root = newConflictResolver().transformGraph( root, context );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( 1, root.getChildren().get( 0 ).getChildren().size() );
        DependencyNode winner = root.getChildren().get( 0 ).getChildren().get( 0 );
        assertEquals( "test", winner.getDependency().getScope() );
        assertEquals( "compile", winner.getData().get( ConflictResolver.NODE_DATA_ORIGINAL_SCOPE ) );
        assertEquals( 1, root.getChildren().get( 1 ).getChildren().size() );
        DependencyNode loser = root.getChildren().get( 1 ).getChildren().get( 0 );
        assertEquals( "test", loser.getDependency().getScope() );
        assertEquals( 0, loser.getChildren().size() );
        assertSame( winner, loser.getData().get( ConflictResolver.NODE_DATA_WINNER ) );
        assertEquals( "compile", loser.getData().get( ConflictResolver.NODE_DATA_ORIGINAL_SCOPE ) );
    }

}
