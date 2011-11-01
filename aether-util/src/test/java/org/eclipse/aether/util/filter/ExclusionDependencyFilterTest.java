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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;
import org.junit.Test;

public class ExclusionDependencyFilterTest
{

    @Test
    public void acceptTest()
    {

        NodeBuilder builder = new NodeBuilder();
        builder.groupId( "com.example.test" ).artifactId( "testArtifact" );
        List<DependencyNode> parents = new LinkedList<DependencyNode>();
        String[] excludes;

        excludes = new String[] { "com.example.test:testArtifact" };
        assertFalse( new ExclusionsDependencyFilter( Arrays.asList( excludes ) ).accept( builder.build(), parents ) );

        excludes = new String[] { "com.example.test:testArtifact", "com.foo:otherArtifact" };
        assertFalse( new ExclusionsDependencyFilter( Arrays.asList( excludes ) ).accept( builder.build(), parents ) );

        excludes = new String[] { "testArtifact" };
        assertFalse( new ExclusionsDependencyFilter( Arrays.asList( excludes ) ).accept( builder.build(), parents ) );

        excludes = new String[] { "otherArtifact" };
        assertTrue( new ExclusionsDependencyFilter( Arrays.asList( excludes ) ).accept( builder.build(), parents ) );

        assertTrue( new ExclusionsDependencyFilter( (Collection<String>) null ).accept( builder.build(), parents ) );
    }
}
