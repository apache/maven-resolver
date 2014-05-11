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
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class AndDependencyTraverserTest
{

    static class DummyDependencyTraverser
        implements DependencyTraverser
    {

        private final boolean traverse;

        private final DependencyTraverser child;

        public DummyDependencyTraverser()
        {
            this( true );
        }

        public DummyDependencyTraverser( boolean traverse )
        {
            this.traverse = traverse;
            this.child = this;
        }

        public DummyDependencyTraverser( boolean traverse, DependencyTraverser child )
        {
            this.traverse = traverse;
            this.child = child;
        }

        public boolean traverseDependency( Dependency dependency )
        {
            return traverse;
        }

        public DependencyTraverser deriveChildTraverser( DependencyCollectionContext context )
        {
            return child;
        }

    }

    @Test
    public void testNewInstance()
    {
        assertNull( AndDependencyTraverser.newInstance( null, null ) );
        DependencyTraverser traverser = new DummyDependencyTraverser();
        assertSame( traverser, AndDependencyTraverser.newInstance( traverser, null ) );
        assertSame( traverser, AndDependencyTraverser.newInstance( null, traverser ) );
        assertSame( traverser, AndDependencyTraverser.newInstance( traverser, traverser ) );
        assertNotNull( AndDependencyTraverser.newInstance( traverser, new DummyDependencyTraverser() ) );
    }

    @Test
    public void testTraverseDependency()
    {
        Dependency dependency = new Dependency( new DefaultArtifact( "g:a:v:1" ), "runtime" );

        DependencyTraverser traverser = new AndDependencyTraverser();
        assertTrue( traverser.traverseDependency( dependency ) );

        traverser =
            new AndDependencyTraverser( new DummyDependencyTraverser( false ), new DummyDependencyTraverser( false ) );
        assertFalse( traverser.traverseDependency( dependency ) );

        traverser =
            new AndDependencyTraverser( new DummyDependencyTraverser( true ), new DummyDependencyTraverser( false ) );
        assertFalse( traverser.traverseDependency( dependency ) );

        traverser =
            new AndDependencyTraverser( new DummyDependencyTraverser( true ), new DummyDependencyTraverser( true ) );
        assertTrue( traverser.traverseDependency( dependency ) );
    }

    @Test
    public void testDeriveChildTraverser_Unchanged()
    {
        DependencyTraverser other1 = new DummyDependencyTraverser( true );
        DependencyTraverser other2 = new DummyDependencyTraverser( false );
        DependencyTraverser traverser = new AndDependencyTraverser( other1, other2 );
        assertSame( traverser, traverser.deriveChildTraverser( null ) );
    }

    @Test
    public void testDeriveChildTraverser_OneRemaining()
    {
        DependencyTraverser other1 = new DummyDependencyTraverser( true );
        DependencyTraverser other2 = new DummyDependencyTraverser( false, null );
        DependencyTraverser traverser = new AndDependencyTraverser( other1, other2 );
        assertSame( other1, traverser.deriveChildTraverser( null ) );
    }

    @Test
    public void testDeriveChildTraverser_ZeroRemaining()
    {
        DependencyTraverser other1 = new DummyDependencyTraverser( true, null );
        DependencyTraverser other2 = new DummyDependencyTraverser( false, null );
        DependencyTraverser traverser = new AndDependencyTraverser( other1, other2 );
        assertNull( traverser.deriveChildTraverser( null ) );
    }

    @Test
    public void testEquals()
    {
        DependencyTraverser other1 = new DummyDependencyTraverser( true );
        DependencyTraverser other2 = new DummyDependencyTraverser( false );
        DependencyTraverser traverser1 = new AndDependencyTraverser( other1, other2 );
        DependencyTraverser traverser2 = new AndDependencyTraverser( other2, other1 );
        DependencyTraverser traverser3 = new AndDependencyTraverser( other1 );
        assertEquals( traverser1, traverser1 );
        assertEquals( traverser1, traverser2 );
        assertNotEquals( traverser1, traverser3 );
        assertNotEquals( traverser1, this );
        assertNotEquals( traverser1, null );
    }

    @Test
    public void testHashCode()
    {
        DependencyTraverser other1 = new DummyDependencyTraverser( true );
        DependencyTraverser other2 = new DummyDependencyTraverser( false );
        DependencyTraverser traverser1 = new AndDependencyTraverser( other1, other2 );
        DependencyTraverser traverser2 = new AndDependencyTraverser( other2, other1 );
        assertEquals( traverser1.hashCode(), traverser2.hashCode() );
    }

}
