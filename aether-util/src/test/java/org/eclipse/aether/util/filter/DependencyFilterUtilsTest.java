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

import java.util.Collections;
import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.test.util.NodeBuilder;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.junit.Test;

/**
 */
public class DependencyFilterUtilsTest
{

    private static List<DependencyNode> PARENTS = Collections.emptyList();

    @Test
    public void testClasspathFilterCompile()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "compile" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterRuntime()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "runtime" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterTest()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "test" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterCompileRuntime()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "compile", "runtime" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterCompilePlusRuntime()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "compile+runtime" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterRuntimeCommaSystem()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "runtime,system" );

        assertTrue( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterNull()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( (String[]) null );

        assertFalse( filter.accept( builder.scope( "compile" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "system" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "provided" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "runtime" ).build(), PARENTS ) );
        assertFalse( filter.accept( builder.scope( "test" ).build(), PARENTS ) );
    }

    @Test
    public void testClasspathFilterUnknownScope()
    {
        NodeBuilder builder = new NodeBuilder().artifactId( "aid" );
        DependencyFilter filter = DependencyFilterUtils.classpathFilter( "compile" );

        assertTrue( filter.accept( builder.scope( "" ).build(), PARENTS ) );
        assertTrue( filter.accept( builder.scope( "unknown" ).build(), PARENTS ) );
    }

}
