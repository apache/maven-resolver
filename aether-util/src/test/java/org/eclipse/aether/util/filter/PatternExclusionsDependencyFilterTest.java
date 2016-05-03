package org.eclipse.aether.util.filter;

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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Test;

public class PatternExclusionsDependencyFilterTest
{

    @Test
    public void acceptTestCornerCases()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.artifactId( "testArtifact" );
        DependencyNode node = builder.build();
        List<DependencyNode> parents = new LinkedList<DependencyNode>();

        // Empty String, Empty List
        assertTrue( dontAccept( node, "" ) );
        assertTrue( new PatternExclusionsDependencyFilter( new LinkedList<String>() ).accept( node, parents ) );
        assertTrue( new PatternExclusionsDependencyFilter( (String[]) null ).accept( node, parents ) );
        assertTrue( new PatternExclusionsDependencyFilter( (VersionScheme) null, "[1,10]" ).accept( node, parents ) );
    }

    @Test
    public void acceptTestMatches()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        // full match
        assertEquals( "com.example.test:testArtifact:jar:1.0.3", true,
                      dontAccept( node, "com.example.test:testArtifact:jar:1.0.3" ) );

        // single wildcard
        assertEquals( "*:testArtifact:jar:1.0.3", true, dontAccept( node, "*:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:*:jar:1.0.3", true, dontAccept( node, "com.example.test:*:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:1.0.3", true,
                      dontAccept( node, "com.example.test:testArtifact:*:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:1.0.3", true,
                      dontAccept( node, "com.example.test:testArtifact:*:1.0.3" ) );

        // implicit wildcard
        assertEquals( ":testArtifact:jar:1.0.3", true, dontAccept( node, ":testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test::jar:1.0.3", true, dontAccept( node, "com.example.test::jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact::1.0.3", true,
                      dontAccept( node, "com.example.test:testArtifact::1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:jar:", true,
                      dontAccept( node, "com.example.test:testArtifact:jar:" ) );

        // multi wildcards
        assertEquals( "*:*:jar:1.0.3", true, dontAccept( node, "*:*:jar:1.0.3" ) );
        assertEquals( "com.example.test:*:*:1.0.3", true, dontAccept( node, "com.example.test:*:*:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*:*", true, dontAccept( node, "com.example.test:testArtifact:*:*" ) );
        assertEquals( "*:testArtifact:jar:*", true, dontAccept( node, "*:testArtifact:jar:*" ) );
        assertEquals( "*:*:jar:*", true, dontAccept( node, "*:*:jar:*" ) );
        assertEquals( ":*:jar:", true, dontAccept( node, ":*:jar:" ) );

        // partial wildcards
        assertEquals( "*.example.test:testArtifact:jar:1.0.3", true,
                      dontAccept( node, "*.example.test:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:*ar:1.0.*", true,
                      dontAccept( node, "com.example.test:testArtifact:*ar:1.0.*" ) );
        assertEquals( "com.example.test:testArtifact:jar:1.0.*", true,
                      dontAccept( node, "com.example.test:testArtifact:jar:1.0.*" ) );
        assertEquals( "*.example.*:testArtifact:jar:1.0.3", true,
                      dontAccept( node, "*.example.*:testArtifact:jar:1.0.3" ) );

        // wildcard as empty string
        assertEquals( "com.example.test*:testArtifact:jar:1.0.3", true,
                      dontAccept( node, "com.example.test*:testArtifact:jar:1.0.3" ) );
    }

    @Test
    public void acceptTestLessToken()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        assertEquals( "com.example.test:testArtifact:jar", true, dontAccept( node, "com.example.test:testArtifact:jar" ) );
        assertEquals( "com.example.test:testArtifact", true, dontAccept( node, "com.example.test:testArtifact" ) );
        assertEquals( "com.example.test", true, dontAccept( node, "com.example.test" ) );

        assertEquals( "com.example.foo", false, dontAccept( node, "com.example.foo" ) );
    }

    @Test
    public void acceptTestMissmatch()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        assertEquals( "OTHER.GROUP.ID:testArtifact:jar:1.0.3", false,
                      dontAccept( node, "OTHER.GROUP.ID:testArtifact:jar:1.0.3" ) );
        assertEquals( "com.example.test:OTHER_ARTIFACT:jar:1.0.3", false,
                      dontAccept( node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3" ) );
        assertEquals( "com.example.test:OTHER_ARTIFACT:jar:1.0.3", false,
                      dontAccept( node, "com.example.test:OTHER_ARTIFACT:jar:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:WAR:1.0.3", false,
                      dontAccept( node, "com.example.test:testArtifact:WAR:1.0.3" ) );
        assertEquals( "com.example.test:testArtifact:jar:SNAPSHOT", false,
                      dontAccept( node, "com.example.test:testArtifact:jar:SNAPSHOT" ) );

        assertEquals( "*:*:war:*", false, dontAccept( node, "*:*:war:*" ) );
        assertEquals( "OTHER.GROUP.ID", false, dontAccept( node, "OTHER.GROUP.ID" ) );
    }

    @Test
    public void acceptTestMoreToken()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );

        DependencyNode node = builder.build();
        assertEquals( "com.example.test:testArtifact:jar:1.0.3:foo", false,
                      dontAccept( node, "com.example.test:testArtifact:jar:1.0.3:foo" ) );
    }

    @Test
    public void acceptTestRange()
    {
        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" ).ext( "jar" ).version( "1.0.3" );
        DependencyNode node = builder.build();

        String prefix = "com.example.test:testArtifact:jar:";

        assertTrue( prefix + "[1.0.3,1.0.4)", dontAcceptVersionRange( node, prefix + "[1.0.3,1.0.4)" ) );
        assertTrue( prefix + "[1.0.3,)", dontAcceptVersionRange( node, prefix + "[1.0.3,)" ) );
        assertTrue( prefix + "[1.0.3,]", dontAcceptVersionRange( node, prefix + "[1.0.3,]" ) );
        assertTrue( prefix + "(,1.0.3]", dontAcceptVersionRange( node, prefix + "(,1.0.3]" ) );
        assertTrue( prefix + "[1.0,]", dontAcceptVersionRange( node, prefix + "[1.0,]" ) );
        assertTrue( prefix + "[1,4]", dontAcceptVersionRange( node, prefix + "[1,4]" ) );
        assertTrue( prefix + "(1,4)", dontAcceptVersionRange( node, prefix + "(1,4)" ) );

        assertTrue( prefix + "(1.0.2,1.0.3]",
                    dontAcceptVersionRange( node, prefix + "(1.0.2,1.0.3]", prefix + "(1.1,)" ) );

        assertFalse( prefix + "(1.0.3,2.0]", dontAcceptVersionRange( node, prefix + "(1.0.3,2.0]" ) );
        assertFalse( prefix + "(1,1.0.2]", dontAcceptVersionRange( node, prefix + "(1,1.0.2]" ) );

        assertFalse( prefix + "(1.0.2,1.0.3)",
                     dontAcceptVersionRange( node, prefix + "(1.0.2,1.0.3)", prefix + "(1.0.3,)" ) );
    }

    private boolean dontAccept( DependencyNode node, String expression )
    {
        return !new PatternExclusionsDependencyFilter( expression ).accept( node, new LinkedList<DependencyNode>() );
    }

    private boolean dontAcceptVersionRange( DependencyNode node, String... expression )
    {
        return !new PatternExclusionsDependencyFilter( new GenericVersionScheme(), expression ).accept( node,
                                                                                                        new LinkedList<DependencyNode>() );
    }

}
