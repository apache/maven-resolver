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
package org.eclipse.aether.util.filter;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Test;

public class PatternInclusionsDependencyFilterTest
    extends AbstractDependencyFilterTest
{

    @Test
    public void acceptTestCornerCases()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId( "testArtifact" );
        DependencyNode node = builder.build();
        List<DependencyNode> parents = new LinkedList<DependencyNode>();

        // Empty String, Empty List
        assertTrue( accept( node, "" ) );
        assertFalse( new PatternInclusionsDependencyFilter( new LinkedList<String>() ).accept( node, parents ) );
        assertFalse( new PatternInclusionsDependencyFilter( (String[]) null ).accept( node, parents ) );
        assertFalse( new PatternInclusionsDependencyFilter( (VersionScheme) null, "[1,10]" ).accept( node, parents ) );
    }

    @Test
    public void acceptTestMatches()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        // full match
        assertEquals( "com.example.test:testArtifact:jar:1.0.3", true, accept( node, "com.example.test:testArtifact:jar:1.0.3" ) );

        // single wildcard
        assertEquals( "*:testArtifact:jar:1.0.3", true, accept( node, "*:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:*:jar:1.0.3", true, accept( node, "com.example.test:*:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:1.0.3", true, accept( node, "com.example.test:testArtifact:*:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:1.0.3", true, accept( node, "com.example.test:testArtifact:*:1.0.3" ) );

        // implicit wildcard
        assertEquals( ":testArtifact:jar:1.0.3", true, accept( node, ":testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test::jar:1.0.3", true, accept( node, "com.example.test::jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact::1.0.3", true, accept( node, "com.example.test:testArtifact::1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:jar:", true, accept( node, "com.example.test:testArtifact:jar:" ) );

        // multi wildcards
        assertEquals( "*:*:jar:1.0.3", true, accept( node, "*:*:jar:1.0.3" ) );
        assertEquals( "com.example.test:*:*:1.0.3", true, accept( node, "com.example.test:*:*:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:*", true, accept( node, "com.example.test:testArtifact:*:*" ) );
        assertEquals( "*:testArtifact:jar:*", true, accept( node, "*:testArtifact:jar:*" ) );
        assertEquals( "*:*:jar:*", true, accept( node, "*:*:jar:*" ) );
        assertEquals( ":*:jar:", true, accept( node, ":*:jar:" ) );

        // partial wildcards
        assertEquals( "*.example.test:testArtifact:jar:1.0.3", true, accept( node, "*.example.test:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*ar:1.0.*", true, accept( node, "com.example.test:testArtifact:*ar:1.0.*" ) );
        assertEquals( "com.example.test:testArtifact:jar:1.0.*", true,
                     accept( node, "com.example.test:testArtifact:jar:1.0.*" ) );
        assertEquals( "*.example.*:testArtifact:jar:1.0.3", true, accept( node, "*.example.*:testArtifact:jar:1.0.3" ) );

        // wildcard as empty string
        assertEquals( "com.example.test*:testArtifact:jar:1.0.3", true, accept( node, "com.example.test*:testArtifact:jar:1.0.3" ) );
    }

    @Test
    public void acceptTestLessToken()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        assertEquals( "com.example.test:testArtifact:jar", true, accept( node, "com.example.test:testArtifact:jar" ) );
        assertEquals( "com.example.test:testArtifact", true, accept( node, "com.example.test:testArtifact" ) );
        assertEquals( "com.example.test", true, accept( node, "com.example.test" ) );

        assertEquals( "com.example.foo", false, accept( node, "com.example.foo" ) );
    }

    @Test
    public void acceptTestMissmatch()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        assertEquals( "OTHER.GROUP.ID:testArtifact:jar:1.0.3", false, accept( node, "OTHER.GROUP.ID:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:OTHER_ARTIFACT:jar:1.0.3", false, accept( node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3" ) );
        assertEquals( "com.example.test:OTHER_ARTIFACT:jar:1.0.3", false, accept( node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:WAR:1.0.3", false, accept( node, "com.example.test:testArtifact:WAR:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:jar:SNAPSHOT", false, accept( node, "com.example.test:testArtifact:jar:SNAPSHOT" ) );

        assertEquals( "*:*:war:*", false, accept( node, "*:*:war:*" ) );
        assertEquals( "OTHER.GROUP.ID", false, accept( node, "OTHER.GROUP.ID" ) );
    }

    @Test
    public void acceptTestMoreToken()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );

        DependencyNode node = builder.build();
        assertEquals( "com.example.test:testArtifact:jar:1.0.3:foo", false, accept( node, "com.example.test:testArtifact:jar:1.0.3:foo" ) );
    }

    @Test
    public void acceptTestRange()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        String prefix = "com.example.test:testArtifact:jar:";

        assertTrue( prefix + "[1.0.3,1.0.4)", acceptVersionRange( node, prefix + "[1.0.3,1.0.4)" ) );
        assertTrue( prefix + "[1.0.3,)", acceptVersionRange( node, prefix + "[1.0.3,)" ) );
        assertTrue( prefix + "[1.0.3,]", acceptVersionRange( node, prefix + "[1.0.3,]" ) );
        assertTrue( prefix + "(,1.0.3]", acceptVersionRange( node, prefix + "(,1.0.3]" ) );
        assertTrue( prefix + "[1.0,]", acceptVersionRange( node, prefix + "[1.0,]" ) );
        assertTrue( prefix + "[1,4]", acceptVersionRange( node, prefix + "[1,4]" ) );
        assertTrue( prefix + "(1,4)", acceptVersionRange( node, prefix + "(1,4)" ) );

        assertTrue( prefix + "(1.0.2,1.0.3]", acceptVersionRange( node, prefix + "(1.0.2,1.0.3]", prefix + "(1.1,)" ) );

        assertFalse( prefix + "(1.0.3,2.0]", acceptVersionRange( node, prefix + "(1.0.3,2.0]" ) );
        assertFalse( prefix + "(1,1.0.2]", acceptVersionRange( node, prefix + "(1,1.0.2]" ) );

        assertFalse( prefix + "(1.0.2,1.0.3)", acceptVersionRange( node, prefix + "(1.0.2,1.0.3)", prefix + "(1.0.3,)" ) );
    }

    public boolean accept( DependencyNode node, String expression )
    {
        return new PatternInclusionsDependencyFilter( expression ).accept( node, new LinkedList<DependencyNode>() );
    }

    public boolean acceptVersionRange( DependencyNode node, String... expression )
    {
        return new PatternInclusionsDependencyFilter( new GenericVersionScheme(), expression ).accept( node,
                                                                                                       new LinkedList<DependencyNode>() );
    }

}
