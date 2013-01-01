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

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.internal.test.util.DependencyGraphParser;
import org.junit.Test;

public class TreeDependencyVisitorTest
{

    private DependencyNode parse( String resource )
        throws Exception
    {
        return new DependencyGraphParser( "visitor/tree/" ).parseResource( resource );
    }

    @Test
    public void testDuplicateSuppression()
        throws Exception
    {
        DependencyNode root = parse( "cycles.txt" );

        RecordingVisitor rec = new RecordingVisitor();
        TreeDependencyVisitor visitor = new TreeDependencyVisitor( rec );
        root.accept( visitor );

        assertEquals( ">a >b >c <c <b >d <d <a ", rec.buffer.toString() );
    }

    private static class RecordingVisitor
        implements DependencyVisitor
    {

        StringBuilder buffer = new StringBuilder( 256 );

        public boolean visitEnter( DependencyNode node )
        {
            buffer.append( '>' ).append( node.getDependency().getArtifact().getArtifactId() ).append( ' ' );
            return true;
        }

        public boolean visitLeave( DependencyNode node )
        {
            buffer.append( '<' ).append( node.getDependency().getArtifact().getArtifactId() ).append( ' ' );
            return true;
        }

    }

}
