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

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.internal.test.util.TestVersionConstraint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConflictResolverTest
{
    @Test
    public void noTransformationRequired() throws RepositoryException
    {
        ConflictResolver resolver = makeDefaultResolver();

        // Foo -> Bar
        DependencyNode fooNode = makeDependencyNode( "group-id", "foo", "1.0" );
        DependencyNode barNode = makeDependencyNode( "group-id", "bar", "1.0" );
        fooNode.setChildren( mutableList( barNode ) );

        DependencyNode transformedNode = resolver.transformGraph(
            fooNode, TestUtils.newTransformationContext( TestUtils.newSession() )
        );

        assertSame( fooNode, transformedNode );
        assertEquals( 1, transformedNode.getChildren().size() );
        assertSame( barNode, transformedNode.getChildren().get( 0 ) );
    }

    @Test
    public void versionClash() throws RepositoryException
    {
        ConflictResolver resolver = makeDefaultResolver();

        // Foo -> Bar -> Baz 2.0
        //  |---> Baz 1.0
        DependencyNode fooNode = makeDependencyNode( "some-group", "foo", "1.0" );
        DependencyNode barNode = makeDependencyNode( "some-group", "bar", "1.0" );
        DependencyNode baz1Node = makeDependencyNode( "some-group", "baz", "1.0" );
        DependencyNode baz2Node = makeDependencyNode( "some-group", "baz", "2.0" );
        fooNode.setChildren( mutableList( barNode, baz1Node ) );
        barNode.setChildren( mutableList( baz2Node ) );

        DependencyNode transformedNode = resolver.transformGraph(
            fooNode, TestUtils.newTransformationContext( TestUtils.newSession() )
        );

        assertSame( fooNode, transformedNode );
        assertEquals( 2, fooNode.getChildren().size() );
        assertSame( barNode, fooNode.getChildren().get( 0 ) );
        assertTrue( barNode.getChildren().isEmpty() );
        assertSame( baz1Node, fooNode.getChildren().get( 1 ) );
    }

    @Test
    public void derivedScopeChange() throws RepositoryException
    {
        ConflictResolver resolver = makeDefaultResolver();

        // Foo -> Bar (test) -> Jaz
        //  |---> Baz -> Jaz
        DependencyNode fooNode = makeDependencyNode( "some-group", "foo", "1.0" );
        DependencyNode barNode = makeDependencyNode( "some-group", "bar", "1.0", "test" );
        DependencyNode bazNode = makeDependencyNode( "some-group", "baz", "1.0" );
        DependencyNode jazNode = makeDependencyNode( "some-group", "jaz", "1.0" );
        fooNode.setChildren( mutableList( barNode, bazNode ) );

        List<DependencyNode> jazList = mutableList( jazNode );
        barNode.setChildren( jazList );
        bazNode.setChildren( jazList );

        DependencyNode transformedNode = resolver.transformGraph(
            fooNode, TestUtils.newTransformationContext( TestUtils.newSession() )
        );

        assertSame( fooNode, transformedNode );
        assertEquals( 2, fooNode.getChildren().size() );
        assertSame( barNode, fooNode.getChildren().get( 0 ) );
        assertEquals( 1, barNode.getChildren().size() );
        assertSame( jazNode, barNode.getChildren().get( 0 ) );
        assertSame( bazNode, fooNode.getChildren().get( 1 ) );
        assertEquals( 1, barNode.getChildren().size() );
        assertSame( jazNode, barNode.getChildren().get( 0 ) );
    }

    @Test
    public void derivedOptionalStatusChange() throws RepositoryException
    {
        ConflictResolver resolver = makeDefaultResolver();

        // Foo -> Bar (optional) -> Jaz
        //  |---> Baz -> Jaz
        DependencyNode fooNode = makeDependencyNode( "some-group", "foo", "1.0" );
        DependencyNode barNode = makeDependencyNode( "some-group", "bar", "1.0" );
        barNode.setOptional(true);
        DependencyNode bazNode = makeDependencyNode( "some-group", "baz", "1.0" );
        DependencyNode jazNode = makeDependencyNode( "some-group", "jaz", "1.0" );
        fooNode.setChildren( mutableList( barNode, bazNode ) );

        List<DependencyNode> jazList = mutableList( jazNode );
        barNode.setChildren( jazList );
        bazNode.setChildren( jazList );

        DependencyNode transformedNode = resolver.transformGraph(
            fooNode, TestUtils.newTransformationContext( TestUtils.newSession() )
        );

        assertSame( fooNode, transformedNode );
        assertEquals( 2, fooNode.getChildren().size() );
        assertSame( barNode, fooNode.getChildren().get( 0 ) );
        assertEquals( 1, barNode.getChildren().size() );
        assertSame( jazNode, barNode.getChildren().get( 0 ) );
        assertSame( bazNode, fooNode.getChildren().get( 1 ) );
        assertEquals( 1, barNode.getChildren().size() );
        assertSame( jazNode, barNode.getChildren().get( 0 ) );
    }

    private static ConflictResolver makeDefaultResolver()
    {
        return new ConflictResolver(
            new NearestVersionSelector(), new JavaScopeSelector(), new SimpleOptionalitySelector(), new JavaScopeDeriver()
        );
    }

    private static DependencyNode makeDependencyNode( String groupId, String artifactId, String version )
    {
        return makeDependencyNode( groupId, artifactId, version, "compile" );
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

    private static List<DependencyNode> mutableList(DependencyNode... nodes)
    {
        return new ArrayList<>( Arrays.asList( nodes ) );
    }
}