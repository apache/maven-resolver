/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.traverser;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;

import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class FatArtifactTraverserTest
{

    @Test
    public void testTraverseDependency()
    {
        DependencyTraverser traverser = new FatArtifactTraverser();
        Map<String, String> props = null;
        assertTrue( traverser.traverseDependency( new Dependency( new DefaultArtifact( "g:a:v:1", props ), "test" ) ) );
        props = Collections.singletonMap( ArtifactProperties.INCLUDES_DEPENDENCIES, "false" );
        assertTrue( traverser.traverseDependency( new Dependency( new DefaultArtifact( "g:a:v:1", props ), "test" ) ) );
        props = Collections.singletonMap( ArtifactProperties.INCLUDES_DEPENDENCIES, "unrecognized" );
        assertTrue( traverser.traverseDependency( new Dependency( new DefaultArtifact( "g:a:v:1", props ), "test" ) ) );
        props = Collections.singletonMap( ArtifactProperties.INCLUDES_DEPENDENCIES, "true" );
        assertFalse( traverser.traverseDependency( new Dependency( new DefaultArtifact( "g:a:v:1", props ), "test" ) ) );
    }

    @Test
    public void testDeriveChildTraverser()
    {
        DependencyTraverser traverser = new FatArtifactTraverser();
        assertSame( traverser, traverser.deriveChildTraverser( null ) );
    }

    @Test
    public void testEquals()
    {
        DependencyTraverser traverser1 = new FatArtifactTraverser();
        DependencyTraverser traverser2 = new FatArtifactTraverser();
        assertEquals( traverser1, traverser1 );
        assertEquals( traverser1, traverser2 );
        assertNotEquals( traverser1, this );
        assertNotEquals( traverser1, null );
    }

    @Test
    public void testHashCode()
    {
        DependencyTraverser traverser1 = new FatArtifactTraverser();
        DependencyTraverser traverser2 = new FatArtifactTraverser();
        assertEquals( traverser1.hashCode(), traverser2.hashCode() );
    }

}
