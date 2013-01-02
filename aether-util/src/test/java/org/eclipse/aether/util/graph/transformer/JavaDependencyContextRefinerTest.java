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

import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.junit.Test;

/**
 */
public class JavaDependencyContextRefinerTest
    extends AbstractDependencyGraphTransformerTest
{

    @Override
    protected DependencyGraphTransformer newTransformer()
    {
        return new JavaDependencyContextRefiner();
    }

    @Override
    protected DependencyGraphParser newParser()
    {
        return new DependencyGraphParser( "transformer/context-refiner/" );
    }

    @Test
    public void testDoNotRefineOtherContext()
        throws Exception
    {
        DependencyNode node = parseLiteral( "gid:aid:ext:cls:ver" );
        node.setRequestContext( "otherContext" );

        DependencyNode refinedNode = transform( node );
        assertEquals( node, refinedNode );
    }

    @Test
    public void testRefineToCompile()
        throws Exception
    {
        String expected = "project/compile";

        DependencyNode node = parseLiteral( "gid:aid:ext:ver:compile" );
        node.setRequestContext( "project" );
        DependencyNode refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );

        node = parseLiteral( "gid:aid:ext:ver:system" );
        node.setRequestContext( "project" );
        refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );

        node = parseLiteral( "gid:aid:ext:ver:provided" );
        node.setRequestContext( "project" );
        refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );
    }

    @Test
    public void testRefineToTest()
        throws Exception
    {
        String expected = "project/test";

        DependencyNode node = parseLiteral( "gid:aid:ext:ver:test" );
        node.setRequestContext( "project" );
        DependencyNode refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );
    }

    @Test
    public void testRefineToRuntime()
        throws Exception
    {
        String expected = "project/runtime";

        DependencyNode node = parseLiteral( "gid:aid:ext:ver:runtime" );
        node.setRequestContext( "project" );
        DependencyNode refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );
    }

    @Test
    public void testDoNotRefineUnknownScopes()
        throws Exception
    {
        String expected = "project";

        DependencyNode node = parseLiteral( "gid:aid:ext:ver:unknownScope" );
        node.setRequestContext( "project" );
        DependencyNode refinedNode = transform( node );
        assertEquals( expected, refinedNode.getRequestContext() );
    }

}
