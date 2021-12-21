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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.impl.StubRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.StubVersionRangeResolver;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.internal.test.util.TestVersionConstraint;
import org.eclipse.aether.util.graph.transformer.*;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test with skip & reconcile mode
 */
public class DependencyResolveReconcilerTest
{
    protected DefaultDependencyCollector collector;

    protected DefaultRepositorySystemSession session;

    protected Dependency newDep( String coords )
    {
        return newDep( coords, "" );
    }

    protected Dependency newDep( String coords, String scope )
    {
        return new Dependency( new DefaultArtifact( coords ), scope );
    }

    protected DependencyGraphTransformer newTransformer()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(),
                new SimpleOptionalitySelector(), new JavaScopeDeriver() );
    }

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

    @Before
    public void setup()
    {
        session = TestUtils.newSession();
        session.setDependencyGraphTransformer( newTransformer() );

        collector = new DefaultDependencyCollector();
        collector.setVersionRangeResolver( new StubVersionRangeResolver() );
        collector.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );
    }


    @Test
    public void testReconcile() throws RepositoryException
    {
        // A -> B -> C 3.0 -> D -> Z  => 1) D of C3.0 is resolved
        // |--> E -> F --> G -> D -> Z  => 2) The D here is skipped as the depth is deeper and reuse the result in step 1
        // |--> C 2.0 -> H  => maven picks C 2.0, so the D of C3.0 in step 1 is no longer valid, need reconcile the skipped D node in step 2
        DependencyNode aNode = makeDependencyNode( "some-group", "A", "1.0" );
        DependencyNode bNode = makeDependencyNode( "some-group", "B", "1.0" );
        DependencyNode c3Node = makeDependencyNode( "some-group", "C", "3.0" );
        DependencyNode dNode = makeDependencyNode( "some-group", "D", "1.0" );
        DependencyNode eNode = makeDependencyNode( "some-group", "E", "1.0" );
        DependencyNode fNode = makeDependencyNode( "some-group", "F", "1.0" );
        DependencyNode c2Node = makeDependencyNode( "some-group", "C", "2.0" );
        DependencyNode gNode = makeDependencyNode( "some-group", "G", "1.0" );
        DependencyNode hNode = makeDependencyNode( "some-group", "H", "1.0" );
        DependencyNode zNode = makeDependencyNode( "some-group", "Z", "1.0" );
        aNode.setChildren( mutableList( bNode, eNode, c2Node ) );
        bNode.setChildren( mutableList( c3Node ) );
        c3Node.setChildren( mutableList( dNode ) );
        dNode.setChildren( mutableList( zNode ) );
        eNode.setChildren( mutableList( fNode ) );
        fNode.setChildren( mutableList( gNode ) );
        c2Node.setChildren( mutableList( hNode ) );

        CollectRequest request = new CollectRequest();
        request.addDependency( new Dependency( aNode.getArtifact(), "compile" ) );
        CollectResult result = new CollectResult( request );
        result.setRoot( aNode );

        //follow the resolve sequence
        DependencyResolveReconciler reconciler = new DependencyResolveReconciler();
        reconciler.cacheChildrenWithDepth( aNode, new ArrayList<>() );
        reconciler.cacheChildrenWithDepth( bNode, mutableList( aNode ) );
        reconciler.cacheChildrenWithDepth( c3Node, mutableList( aNode, bNode ) );
        reconciler.cacheChildrenWithDepth( dNode, mutableList( aNode, bNode, c3Node ) );
        reconciler.cacheChildrenWithDepth( eNode, mutableList( aNode ) );
        reconciler.cacheChildrenWithDepth( fNode, mutableList( aNode, eNode ) );
        reconciler.cacheChildrenWithDepth( gNode, mutableList( aNode, eNode, fNode ) );
        DependencyNode clonedDNode = new DefaultDependencyNode( dNode );
        gNode.setChildren( mutableList( clonedDNode ) );
        reconciler.addSkip( clonedDNode,
                new DataPool.GraphKey( clonedDNode.getArtifact(), null, null, null, null, null ),
                Arrays.asList( newDep( "some-group:Z:ext:1.0" ) ), mutableList( aNode, eNode, fNode, gNode ),
                mutableList( aNode, bNode, c3Node ) );
        reconciler.cacheChildrenWithDepth( c2Node, mutableList( aNode ) );

        Collection<DependencyResolveReconciler.DependencyResolveSkip> skips =
                reconciler.getNodesToReconcile( session, result );
        assertEquals( skips.size(), 1 );

        DependencyResolveReconciler.DependencyResolveSkip skip =
                skips.toArray( new DependencyResolveReconciler.DependencyResolveSkip[0] )[0];
        assertEquals( skip.depth, 5 );
        assertEquals( skip.node.getArtifact().getArtifactId(), "D" );
        assertEquals( skip.parentPathsOfCandidateLowerDepth, mutableList( aNode, bNode, c3Node ) );
    }
}
