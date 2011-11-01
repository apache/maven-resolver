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
package org.eclipse.aether.util.graph.transformer;

import static org.junit.Assert.*;

import java.util.Map;

import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.NodeBuilder;
import org.eclipse.aether.util.graph.transformer.ConflictMarker;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.junit.Test;

/**
 */
public class ConflictMarkerTest
{

    private DependencyGraphTransformationContext newContext()
    {
        return new SimpleDependencyGraphTransformationContext();
    }

    @Test
    public void testSimple()
        throws Exception
    {
        NodeBuilder builder = new NodeBuilder();

        DependencyNode root = builder.build();
        root.getChildren().add( builder.artifactId( "a" ).build() );
        root.getChildren().add( builder.artifactId( "b" ).build() );

        DependencyGraphTransformationContext context = newContext();

        assertSame( root, new ConflictMarker().transformGraph( root, context ) );

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
        NodeBuilder builder = new NodeBuilder();

        DependencyNode root = builder.build();
        root.getChildren().add( builder.artifactId( "a" ).build() );
        root.getChildren().add( builder.artifactId( "a" ).reloc( "reloc" ).build() );

        DependencyGraphTransformationContext context = newContext();

        assertSame( root, new ConflictMarker().transformGraph( root, context ) );

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
        NodeBuilder builder = new NodeBuilder();

        DependencyNode root = builder.build();
        root.getChildren().add( builder.artifactId( "a" ).reloc( "reloc" ).build() );
        root.getChildren().add( builder.artifactId( "a" ).build() );

        DependencyGraphTransformationContext context = newContext();

        assertSame( root, new ConflictMarker().transformGraph( root, context ) );

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
        NodeBuilder builder = new NodeBuilder();

        DependencyNode root = builder.build();
        root.getChildren().add( builder.artifactId( "a" ).build() );
        root.getChildren().add( builder.artifactId( "b" ).build() );
        root.getChildren().add( builder.artifactId( "c" ).reloc( "a" ).reloc( "b" ).build() );

        DependencyGraphTransformationContext context = newContext();

        assertSame( root, new ConflictMarker().transformGraph( root, context ) );

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
