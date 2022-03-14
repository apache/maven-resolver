package org.eclipse.aether.internal.impl.collect;
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

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.internal.test.util.TestVersionConstraint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class DependencyResolutionSkipperTest
{
    private static DependencyNode makeDependencyNode( String groupId, String artifactId, String version )
    {
        return makeDependencyNode( groupId, artifactId, version, "compile" );
    }

    private static List<DependencyNode> mutableList( DependencyNode... nodes )
    {
        return new ArrayList<>( Arrays.asList( nodes ) );
    }

    private static DependencyNode makeDependencyNode( String groupId, String artifactId, String version, String scope )
    {
        DefaultDependencyNode node = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( groupId + ':' + artifactId + ':' + version ), scope )
        );
        node.setVersion( new TestVersion( version ) );
        node.setVersionConstraint( new TestVersionConstraint( node.getVersion() ) );
        return node;
    }

    @Test
    public void testSkipVersionConflict() throws RepositoryException
    {
        // A -> B -> C 3.0 -> D   => C3.0 SHOULD BE SKIPPED
        // | -> E -> F -> G
        // | -> C 2.0 -> H  => C2.0 is the winner
        DependencyNode aNode = makeDependencyNode( "some-group", "A", "1.0" );
        DependencyNode bNode = makeDependencyNode( "some-group", "B", "1.0" );
        DependencyNode c3Node = makeDependencyNode( "some-group", "C", "3.0" );
        DependencyNode dNode = makeDependencyNode( "some-group", "D", "1.0" );
        DependencyNode eNode = makeDependencyNode( "some-group", "E", "1.0" );
        DependencyNode fNode = makeDependencyNode( "some-group", "F", "1.0" );
        DependencyNode c2Node = makeDependencyNode( "some-group", "C", "2.0" );
        DependencyNode gNode = makeDependencyNode( "some-group", "G", "1.0" );
        DependencyNode hNode = makeDependencyNode( "some-group", "H", "1.0" );

        aNode.setChildren( mutableList( bNode, eNode, c2Node ) );
        bNode.setChildren( mutableList( c3Node ) );
        c3Node.setChildren( mutableList( dNode ) );
        eNode.setChildren( mutableList( fNode ) );
        fNode.setChildren( mutableList( gNode ) );
        c2Node.setChildren( mutableList( hNode ) );

        //follow the BFS resolve sequence
        DefaultDependencyResolutionSkipper skipper = new DefaultDependencyResolutionSkipper();
        assertFalse( skipper.skipResolution( aNode, new ArrayList<>() ) );
        skipper.cache( aNode, new ArrayList<>() );
        assertFalse( skipper.skipResolution( bNode, mutableList( aNode ) ) );
        skipper.cache( bNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( eNode, mutableList( aNode ) ) );
        skipper.cache( eNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( c2Node, mutableList( aNode ) ) );
        skipper.cache( c2Node, mutableList( aNode ) );
        assertTrue( skipper.skipResolution( c3Node, mutableList( aNode, bNode ) ) );//version conflict
        assertFalse( skipper.skipResolution( fNode, mutableList( aNode, eNode ) ) );
        skipper.cache( fNode, mutableList( aNode, eNode ) );
        assertFalse( skipper.skipResolution( gNode, mutableList( aNode, eNode, fNode ) ) );
        skipper.cache( gNode, mutableList( aNode, eNode, fNode ) );

        Map<DependencyNode, DefaultDependencyResolutionSkipper.DependencyResolutionResult> results = skipper.getResults();
        assertEquals( results.size(), 7 );

        List<DefaultDependencyResolutionSkipper.DependencyResolutionResult> skipped =
                results.entrySet().stream().filter( n -> n.getValue().skippedAsVersionConflict )
                        .map( s -> s.getValue() ).collect(
                                Collectors.toList() );
        assertEquals( skipped.size(), 1 );
        assertTrue( skipped.get( 0 ).current == c3Node );
    }

    @Test
    public void testSkipDeeperDuplicateNode() throws RepositoryException
    {
        // A -> B
        // |--> C -> B  => B here will be skipped
        // |--> D -> C  => C here will be skipped
        DependencyNode aNode = makeDependencyNode( "some-group", "A", "1.0" );
        DependencyNode bNode = makeDependencyNode( "some-group", "B", "1.0" );
        DependencyNode cNode = makeDependencyNode( "some-group", "C", "1.0" );
        DependencyNode dNode = makeDependencyNode( "some-group", "D", "1.0" );
        DependencyNode b1Node = new DefaultDependencyNode( bNode );
        DependencyNode c1Node = new DefaultDependencyNode( cNode );

        aNode.setChildren( mutableList( bNode, cNode, dNode ) );
        bNode.setChildren( new ArrayList<>() );
        cNode.setChildren( mutableList( b1Node ) );
        dNode.setChildren( mutableList( c1Node ) );

        //follow the BFS resolve sequence
        DefaultDependencyResolutionSkipper skipper = new DefaultDependencyResolutionSkipper();
        assertFalse( skipper.skipResolution( aNode, new ArrayList<>() ) );
        skipper.cache( aNode, new ArrayList<>() );
        assertFalse( skipper.skipResolution( bNode, mutableList( aNode ) ) );
        skipper.cache( bNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( cNode, mutableList( aNode ) ) );
        skipper.cache( cNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( dNode, mutableList( aNode ) ) );
        skipper.cache( dNode, mutableList( aNode ) );

        assertTrue( skipper.skipResolution( b1Node, mutableList( aNode, cNode ) ) );
        skipper.cache( b1Node, mutableList( aNode, cNode ) );

        assertTrue( skipper.skipResolution( c1Node, mutableList( aNode, dNode ) ) );
        skipper.cache( c1Node, mutableList( aNode, dNode ) );

        Map<DependencyNode, DefaultDependencyResolutionSkipper.DependencyResolutionResult> results = skipper.getResults();
        assertEquals( results.size(), 6 );

        List<DefaultDependencyResolutionSkipper.DependencyResolutionResult> skipped =
                results.entrySet().stream().filter( n -> n.getValue().skippedAsDuplicate )
                        .map( s -> s.getValue() ).collect(
                                Collectors.toList() );
        assertEquals( skipped.size(), 2 );
        assertTrue( skipped.get( 0 ).current == b1Node );
        assertTrue( skipped.get( 1 ).current == c1Node );
    }


    @Test
    public void testForceResolution() throws RepositoryException
    {
        // A -> B -> C -> D => 3rd D here will be force-resolved
        // |--> C -> D => 2nd D will be force-resolved
        // |--> D => 1st D to resolve
        DependencyNode aNode = makeDependencyNode( "some-group", "A", "1.0" );
        DependencyNode bNode = makeDependencyNode( "some-group", "B", "1.0" );
        DependencyNode cNode = makeDependencyNode( "some-group", "C", "1.0" );
        DependencyNode dNode = makeDependencyNode( "some-group", "D", "1.0" );
        DependencyNode c1Node = new DefaultDependencyNode( cNode );
        DependencyNode d1Node = new DefaultDependencyNode( dNode );
        DependencyNode d2Node = new DefaultDependencyNode( dNode );

        aNode.setChildren( mutableList( bNode, cNode, dNode ) );
        bNode.setChildren( mutableList( c1Node ) );
        c1Node.setChildren( mutableList( d2Node ) );
        cNode.setChildren( mutableList( d1Node ) );
        dNode.setChildren( new ArrayList<>() );

        //follow the BFS resolve sequence
        DefaultDependencyResolutionSkipper skipper = new DefaultDependencyResolutionSkipper();
        assertFalse( skipper.skipResolution( aNode, new ArrayList<>() ) );
        skipper.cache( aNode, new ArrayList<>() );
        assertFalse( skipper.skipResolution( bNode, mutableList( aNode ) ) );
        skipper.cache( bNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( cNode, mutableList( aNode ) ) );
        skipper.cache( cNode, mutableList( aNode ) );
        assertFalse( skipper.skipResolution( dNode, mutableList( aNode ) ) );
        skipper.cache( dNode, mutableList( aNode ) );

        assertFalse( skipper.skipResolution( c1Node, mutableList( aNode, bNode ) ) );
        skipper.cache( c1Node, mutableList( aNode, bNode ) );

        assertFalse( skipper.skipResolution( d1Node, mutableList( aNode, cNode ) ) );
        skipper.cache( d1Node, mutableList( aNode, cNode ) );

        assertFalse( skipper.skipResolution( d2Node, mutableList( aNode, bNode, c1Node ) ) );
        skipper.cache( d2Node, mutableList( aNode, bNode, c1Node ) );

        Map<DependencyNode, DefaultDependencyResolutionSkipper.DependencyResolutionResult> results = skipper.getResults();
        assertEquals( results.size(), 7 );

        List<DefaultDependencyResolutionSkipper.DependencyResolutionResult> forceResolved =
                results.entrySet().stream().filter( n -> n.getValue().forceResolution )
                        .map( s -> s.getValue() ).collect(
                                Collectors.toList() );
        assertEquals( forceResolved.size(), 3 );
        assertTrue( forceResolved.get( 0 ).current == c1Node );
        assertTrue( forceResolved.get( 1 ).current == d1Node );
        assertTrue( forceResolved.get( 2 ).current == d2Node );
    }

}