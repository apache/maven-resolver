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
package org.eclipse.aether.util.graph.visitor;

import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class PreorderNodeListGeneratorTest
{

    private DependencyNode parse( String resource )
        throws Exception
    {
        return new DependencyGraphParser( "visitor/ordered-list/" ).parseResource( resource );
    }

    private void assertSequence( List<DependencyNode> actual, String... expected )
    {
        assertEquals( actual.toString(), expected.length, actual.size() );
        for ( int i = 0; i < expected.length; i++ )
        {
            DependencyNode node = actual.get( i );
            assertEquals( actual.toString(), expected[i], node.getDependency().getArtifact().getArtifactId() );
        }
    }

    @Test
    public void testOrdering()
        throws Exception
    {
        DependencyNode root = parse( "simple.txt" );

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept( visitor );

        assertSequence( visitor.getNodes(), "a", "b", "c", "d", "e" );
    }

    @Test
    public void testDuplicateSuppression()
        throws Exception
    {
        DependencyNode root = parse( "cycles.txt" );

        PreorderNodeListGenerator visitor = new PreorderNodeListGenerator();
        root.accept( visitor );

        assertSequence( visitor.getNodes(), "a", "b", "c", "d", "e" );
    }

}
