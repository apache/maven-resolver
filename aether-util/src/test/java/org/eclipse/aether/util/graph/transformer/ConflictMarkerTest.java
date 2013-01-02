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
package org.eclipse.aether.util.graph.transformer;

import static org.junit.Assert.*;

import java.util.Map;

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.transformer.ConflictMarker;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.junit.Test;

/**
 */
public class ConflictMarkerTest
    extends AbstractDependencyGraphTransformerTest
{

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new ConflictMarker();
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/conflict-marker/" );
    }

    @Test
    public void testSimple()
        throws Exception
    {
        DependencyNode root = parseResource( "simple.txt" );

        assertSame( root, transform( root ) );

        Map<?, ?> ids = (Map<?, ?>) context.get( TransformationContextKeys.CONFLICT_IDS );
        assertNotNull( ids );

        assertNull( ids.get( root ) );
        assertNotNull( ids.get( root.getChildren().get( 0 ) ) );
        assertNotNull( ids.get( root.getChildren().get( 1 ) ) );
        assertNotSame( ids.get( root.getChildren().get( 0 ) ), ids.get( root.getChildren().get( 1 ) ) );
        assertFalse( ids.get( root.getChildren().get( 0 ) ).equals( ids.get( root.getChildren().get( 1 ) ) ) );
    }

    @Test
    public void testRelocation1()
        throws Exception
    {
        DependencyNode root = parseResource( "relocation1.txt" );

        assertSame( root, transform( root ) );

        Map<?, ?> ids = (Map<?, ?>) context.get( TransformationContextKeys.CONFLICT_IDS );
        assertNotNull( ids );

        assertNull( ids.get( root ) );
        assertNotNull( ids.get( root.getChildren().get( 0 ) ) );
        assertNotNull( ids.get( root.getChildren().get( 1 ) ) );
        assertSame( ids.get( root.getChildren().get( 0 ) ), ids.get( root.getChildren().get( 1 ) ) );
    }

    @Test
    public void testRelocation2()
        throws Exception
    {
        DependencyNode root = parseResource( "relocation2.txt" );

        assertSame( root, transform( root ) );

        Map<?, ?> ids = (Map<?, ?>) context.get( TransformationContextKeys.CONFLICT_IDS );
        assertNotNull( ids );

        assertNull( ids.get( root ) );
        assertNotNull( ids.get( root.getChildren().get( 0 ) ) );
        assertNotNull( ids.get( root.getChildren().get( 1 ) ) );
        assertSame( ids.get( root.getChildren().get( 0 ) ), ids.get( root.getChildren().get( 1 ) ) );
    }

    @Test
    public void testRelocation3()
        throws Exception
    {
        DependencyNode root = parseResource( "relocation3.txt" );

        assertSame( root, transform( root ) );

        Map<?, ?> ids = (Map<?, ?>) context.get( TransformationContextKeys.CONFLICT_IDS );
        assertNotNull( ids );

        assertNull( ids.get( root ) );
        assertNotNull( ids.get( root.getChildren().get( 0 ) ) );
        assertNotNull( ids.get( root.getChildren().get( 1 ) ) );
        assertNotNull( ids.get( root.getChildren().get( 2 ) ) );
        assertSame( ids.get( root.getChildren().get( 0 ) ), ids.get( root.getChildren().get( 1 ) ) );
        assertSame( ids.get( root.getChildren().get( 1 ) ), ids.get( root.getChildren().get( 2 ) ) );
    }

}
