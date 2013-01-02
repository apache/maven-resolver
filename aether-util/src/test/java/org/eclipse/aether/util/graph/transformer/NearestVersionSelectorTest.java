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

    @Override
    protected ConflictResolver newTransformer()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(), new JavaScopeDeriver() );
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/version-resolver/" );
    }

    @Test
    public void testSelectHighestVersionFromMultipleVersionsAtSameLevel()
        throws Exception
    {
        DependencyNode root = parseResource( "sibling-versions.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 1, root.getChildren().size() );
        assertEquals( "3", root.getChildren().get( 0 ).getDependency().getArtifact().getVersion() );
    }

    @Test
    public void testSelectedVersionAtDeeperLevelThanOriginallySeen()
        throws Exception
    {
        DependencyNode root = parseResource( "nearest-underneath-loser-a.txt" );

        assertSame( root, transform( root ) );

        List<DependencyNode> trail = find( root, "j" );
        assertEquals( 5, trail.size() );
    }

    @Test
    public void testNearestDirtyVersionUnderneathRemovedNode()
        throws Exception
    {
        DependencyNode root = parseResource( "nearest-underneath-loser-b.txt" );

        assertSame( root, transform( root ) );

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

        assertSame( root, transform( root ) );

        List<DependencyNode> trail = find( root, "x" );
        assertEquals( 3, trail.size() );
        assertSame( x2, trail.get( 0 ) );
    }

    @Test
    public void testCyclicConflictIdGraph()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict-id-cycle.txt" );

        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( "a", root.getChildren().get( 0 ).getDependency().getArtifact().getArtifactId() );
        assertEquals( "b", root.getChildren().get( 1 ).getDependency().getArtifact().getArtifactId() );
        assertTrue( root.getChildren().get( 0 ).getChildren().isEmpty() );
        assertTrue( root.getChildren().get( 1 ).getChildren().isEmpty() );
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

        assertSame( root, transform( root ) );
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

        assertSame( root, transform( root ) );
    }

    @Test
    public void testConflictGroupCompletelyDroppedFromResolvedTree()
        throws Exception
    {
        DependencyNode root = parseResource( "dead-conflict-group.txt" );

        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( "a", root.getChildren().get( 0 ).getDependency().getArtifact().getArtifactId() );
        assertEquals( "b", root.getChildren().get( 1 ).getDependency().getArtifact().getArtifactId() );
        assertTrue( root.getChildren().get( 0 ).getChildren().isEmpty() );
        assertTrue( root.getChildren().get( 1 ).getChildren().isEmpty() );
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

        assertSame( root, transform( root ) );

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
        DependencyNode root = parseResource( "cycle.txt" );

        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( 1, root.getChildren().get( 0 ).getChildren().size() );
        assertEquals( 0, root.getChildren().get( 0 ).getChildren().get( 0 ).getChildren().size() );
        assertEquals( 0, root.getChildren().get( 1 ).getChildren().size() );
    }

    @Test
    public void testLoop()
        throws Exception
    {
        DependencyNode root = parseResource( "loop.txt" );

        assertSame( root, transform( root ) );

        assertEquals( 0, root.getChildren().size() );
    }

    @Test
    public void testOverlappingCycles()
        throws Exception
    {
        DependencyNode root = parseResource( "overlapping-cycles.txt" );

        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
    }

    @Test
    public void testScopeDerivationAndConflictResolutionCantHappenForAllNodesBeforeVersionSelection()
        throws Exception
    {
        DependencyNode root = parseResource( "scope-vs-version.txt" );

        assertSame( root, transform( root ) );

        DependencyNode[] nodes = find( root, "y" ).toArray( new DependencyNode[0] );
        assertEquals( 3, nodes.length );
        assertEquals( "test", nodes[1].getDependency().getScope() );
        assertEquals( "test", nodes[0].getDependency().getScope() );
    }

    @Test
    public void testVerboseMode()
        throws Exception
    {
        DependencyNode root = parseResource( "verbose.txt" );

        session.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.TRUE );
        assertSame( root, transform( root ) );

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
