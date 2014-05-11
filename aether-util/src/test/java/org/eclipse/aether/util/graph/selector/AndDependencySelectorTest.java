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
package org.eclipse.aether.util.graph.selector;

import static org.junit.Assert.*;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class AndDependencySelectorTest
{

    static class DummyDependencySelector
        implements DependencySelector
    {

        private final boolean select;

        private final DependencySelector child;

        public DummyDependencySelector()
        {
            this( true );
        }

        public DummyDependencySelector( boolean select )
        {
            this.select = select;
            this.child = this;
        }

        public DummyDependencySelector( boolean select, DependencySelector child )
        {
            this.select = select;
            this.child = child;
        }

        public boolean selectDependency( Dependency dependency )
        {
            return select;
        }

        public DependencySelector deriveChildSelector( DependencyCollectionContext context )
        {
            return child;
        }

    }

    @Test
    public void testNewInstance()
    {
        assertNull( AndDependencySelector.newInstance( null, null ) );
        DependencySelector selector = new DummyDependencySelector();
        assertSame( selector, AndDependencySelector.newInstance( selector, null ) );
        assertSame( selector, AndDependencySelector.newInstance( null, selector ) );
        assertSame( selector, AndDependencySelector.newInstance( selector, selector ) );
        assertNotNull( AndDependencySelector.newInstance( selector, new DummyDependencySelector() ) );
    }

    @Test
    public void testTraverseDependency()
    {
        Dependency dependency = new Dependency( new DefaultArtifact( "g:a:v:1" ), "runtime" );

        DependencySelector selector = new AndDependencySelector();
        assertTrue( selector.selectDependency( dependency ) );

        selector =
            new AndDependencySelector( new DummyDependencySelector( false ), new DummyDependencySelector( false ) );
        assertFalse( selector.selectDependency( dependency ) );

        selector =
            new AndDependencySelector( new DummyDependencySelector( true ), new DummyDependencySelector( false ) );
        assertFalse( selector.selectDependency( dependency ) );

        selector = new AndDependencySelector( new DummyDependencySelector( true ), new DummyDependencySelector( true ) );
        assertTrue( selector.selectDependency( dependency ) );
    }

    @Test
    public void testDeriveChildSelector_Unchanged()
    {
        DependencySelector other1 = new DummyDependencySelector( true );
        DependencySelector other2 = new DummyDependencySelector( false );
        DependencySelector selector = new AndDependencySelector( other1, other2 );
        assertSame( selector, selector.deriveChildSelector( null ) );
    }

    @Test
    public void testDeriveChildSelector_OneRemaining()
    {
        DependencySelector other1 = new DummyDependencySelector( true );
        DependencySelector other2 = new DummyDependencySelector( false, null );
        DependencySelector selector = new AndDependencySelector( other1, other2 );
        assertSame( other1, selector.deriveChildSelector( null ) );
    }

    @Test
    public void testDeriveChildSelector_ZeroRemaining()
    {
        DependencySelector other1 = new DummyDependencySelector( true, null );
        DependencySelector other2 = new DummyDependencySelector( false, null );
        DependencySelector selector = new AndDependencySelector( other1, other2 );
        assertNull( selector.deriveChildSelector( null ) );
    }

    @Test
    public void testEquals()
    {
        DependencySelector other1 = new DummyDependencySelector( true );
        DependencySelector other2 = new DummyDependencySelector( false );
        DependencySelector selector1 = new AndDependencySelector( other1, other2 );
        DependencySelector selector2 = new AndDependencySelector( other2, other1 );
        DependencySelector selector3 = new AndDependencySelector( other1 );
        assertEquals( selector1, selector1 );
        assertEquals( selector1, selector2 );
        assertNotEquals( selector1, selector3 );
        assertNotEquals( selector1, this );
        assertNotEquals( selector1, null );
    }

    @Test
    public void testHashCode()
    {
        DependencySelector other1 = new DummyDependencySelector( true );
        DependencySelector other2 = new DummyDependencySelector( false );
        DependencySelector selector1 = new AndDependencySelector( other1, other2 );
        DependencySelector selector2 = new AndDependencySelector( other2, other1 );
        assertEquals( selector1.hashCode(), selector2.hashCode() );
    }

}
