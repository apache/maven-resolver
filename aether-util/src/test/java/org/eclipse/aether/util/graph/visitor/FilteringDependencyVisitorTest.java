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

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class FilteringDependencyVisitorTest
{

    private DependencyNode parse( String resource )
        throws Exception
    {
        return new DependencyGraphParser( "visitor/filtering/" ).parseResource( resource );
    }

    @Test
    public void testFilterCalledWithProperParentStack()
        throws Exception
    {
        DependencyNode root = parse( "parents.txt" );

        final StringBuilder buffer = new StringBuilder( 256 );
        DependencyFilter filter = new DependencyFilter()
        {
            public boolean accept( DependencyNode node, List<DependencyNode> parents )
            {
                for ( DependencyNode parent : parents )
                {
                    buffer.append( parent.getDependency().getArtifact().getArtifactId() );
                }
                buffer.append( "," );
                return false;
            }
        };

        FilteringDependencyVisitor visitor = new FilteringDependencyVisitor( new PreorderNodeListGenerator(), filter );
        root.accept( visitor );

        assertEquals( ",a,ba,cba,a,ea,", buffer.toString() );
    }

}
