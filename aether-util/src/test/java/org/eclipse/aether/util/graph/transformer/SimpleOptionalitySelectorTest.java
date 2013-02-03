/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.transformer;

import static org.junit.Assert.*;

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class SimpleOptionalitySelectorTest
    extends AbstractDependencyGraphTransformerTest
{

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new ConflictResolver( new NearestVersionSelector(), new JavaScopeSelector(),
                                     new SimpleOptionalitySelector(), new JavaScopeDeriver() );
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/optionality-selector/" );
    }

    @Test
    public void testDeriveOptionality()
        throws Exception
    {
        DependencyNode root = parseResource( "derive.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( true, root.getChildren().get( 0 ).getDependency().isOptional() );
        assertEquals( true, root.getChildren().get( 0 ).getChildren().get( 0 ).getDependency().isOptional() );
        assertEquals( false, root.getChildren().get( 1 ).getDependency().isOptional() );
        assertEquals( false, root.getChildren().get( 1 ).getChildren().get( 0 ).getDependency().isOptional() );
    }

    @Test
    public void testResolveOptionalityConflict_NonOptionalWins()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( true, root.getChildren().get( 0 ).getDependency().isOptional() );
        assertEquals( false, root.getChildren().get( 0 ).getChildren().get( 0 ).getDependency().isOptional() );
    }

    @Test
    public void testResolveOptionalityConflict_DirectDeclarationWins()
        throws Exception
    {
        DependencyNode root = parseResource( "conflict-direct-dep.txt" );
        assertSame( root, transform( root ) );

        assertEquals( 2, root.getChildren().size() );
        assertEquals( true, root.getChildren().get( 1 ).getDependency().isOptional() );
    }

}
