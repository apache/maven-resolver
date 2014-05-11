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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class StaticDependencyTraverserTest
{

    @Test
    public void testTraverseDependency()
    {
        Dependency dependency = new Dependency( new DefaultArtifact( "g:a:v:1" ), "runtime" );
        DependencyTraverser traverser = new StaticDependencyTraverser( true );
        assertTrue( traverser.traverseDependency( dependency ) );
        traverser = new StaticDependencyTraverser( false );
        assertFalse( traverser.traverseDependency( dependency ) );
    }

    @Test
    public void testDeriveChildTraverser()
    {
        DependencyTraverser traverser = new StaticDependencyTraverser( true );
        assertSame( traverser, traverser.deriveChildTraverser( null ) );
    }

    @Test
    public void testEquals()
    {
        DependencyTraverser traverser1 = new StaticDependencyTraverser( true );
        DependencyTraverser traverser2 = new StaticDependencyTraverser( true );
        DependencyTraverser traverser3 = new StaticDependencyTraverser( false );
        assertEquals( traverser1, traverser1 );
        assertEquals( traverser1, traverser2 );
        assertNotEquals( traverser1, traverser3 );
        assertNotEquals( traverser1, this );
        assertNotEquals( traverser1, null );
    }

    @Test
    public void testHashCode()
    {
        DependencyTraverser traverser1 = new StaticDependencyTraverser( true );
        DependencyTraverser traverser2 = new StaticDependencyTraverser( true );
        assertEquals( traverser1.hashCode(), traverser2.hashCode() );
    }

}
