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
package org.eclipse.aether.util.graph.visitor;

import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class PathRecordingDependencyVisitorTest
{

    private DependencyNode parse( String resource )
        throws Exception
    {
        return new DependencyGraphParser( "visitor/path-recorder/" ).parseResource( resource );
    }

    private void assertPath( List<DependencyNode> actual, String... expected )
    {
        assertEquals( actual.toString(), expected.length, actual.size() );
        for ( int i = 0; i < expected.length; i++ )
        {
            DependencyNode node = actual.get( i );
            assertEquals( actual.toString(), expected[i], node.getDependency().getArtifact().getArtifactId() );
        }
    }

    @Test
    public void testGetPaths_RecordsMatchesBeneathUnmatchedParents()
        throws Exception
    {
        DependencyNode root = parse( "simple.txt" );

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( new ArtifactMatcher() );
        root.accept( visitor );

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals( paths.toString(), 2, paths.size() );
        assertPath( paths.get( 0 ), "a", "b", "x" );
        assertPath( paths.get( 1 ), "a", "x" );
    }

    @Test
    public void testGetPaths_DoesNotRecordMatchesBeneathMatchedParents()
        throws Exception
    {
        DependencyNode root = parse( "nested.txt" );

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( new ArtifactMatcher() );
        root.accept( visitor );

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals( paths.toString(), 1, paths.size() );
        assertPath( paths.get( 0 ), "x" );
    }

    @Test
    public void testGetPaths_RecordsMatchesBeneathMatchedParentsIfRequested()
        throws Exception
    {
        DependencyNode root = parse( "nested.txt" );

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( new ArtifactMatcher(), false );
        root.accept( visitor );

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals( paths.toString(), 3, paths.size() );
        assertPath( paths.get( 0 ), "x" );
        assertPath( paths.get( 1 ), "x", "a", "y" );
        assertPath( paths.get( 2 ), "x", "y" );
    }

    @Test
    public void testFilterCalledWithProperParentStack()
        throws Exception
    {
        DependencyNode root = parse( "parents.txt" );

        final StringBuilder buffer = new StringBuilder( 256 );
        DependencyFilter filter = new DependencyFilter()
        {
            public boolean accept( DependencyNode node, List<DependencyNode> parents )
            {
                for ( DependencyNode parent : parents )
                {
                    buffer.append( parent.getDependency().getArtifact().getArtifactId() );
                }
                buffer.append( "," );
                return false;
            }
        };

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( filter );
        root.accept( visitor );

        assertEquals( ",a,ba,cba,a,ea,", buffer.toString() );
    }

    @Test
    public void testGetPaths_HandlesCycles()
        throws Exception
    {
        DependencyNode root = parse( "cycle.txt" );

        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( new ArtifactMatcher(), false );
        root.accept( visitor );

        List<List<DependencyNode>> paths = visitor.getPaths();
        assertEquals( paths.toString(), 4, paths.size() );
        assertPath( paths.get( 0 ), "a", "b", "x" );
        assertPath( paths.get( 1 ), "a", "x" );
        assertPath( paths.get( 2 ), "a", "x", "b", "x" );
        assertPath( paths.get( 3 ), "a", "x", "x" );
    }

    private static class ArtifactMatcher
        implements DependencyFilter
    {
        public boolean accept( DependencyNode node, List<DependencyNode> parents )
        {
            return node.getDependency() != null && node.getDependency().getArtifact().getGroupId().equals( "match" );
        }
    }

}
