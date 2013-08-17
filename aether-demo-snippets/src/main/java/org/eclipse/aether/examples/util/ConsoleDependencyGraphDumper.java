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
package org.eclipse.aether.examples.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that dumps the graph to the console.
 */
public class ConsoleDependencyGraphDumper
    implements DependencyVisitor
{

    private PrintStream out;

    private List<ChildInfo> childInfos = new ArrayList<ChildInfo>();

    public ConsoleDependencyGraphDumper()
    {
        this( null );
    }

    public ConsoleDependencyGraphDumper( PrintStream out )
    {
        this.out = ( out != null ) ? out : System.out;
    }

    public boolean visitEnter( DependencyNode node )
    {
        out.println( formatIndentation() + formatNode( node ) );
        childInfos.add( new ChildInfo( node.getChildren().size() ) );
        return true;
    }

    private String formatIndentation()
    {
        StringBuilder buffer = new StringBuilder( 128 );
        for ( Iterator<ChildInfo> it = childInfos.iterator(); it.hasNext(); )
        {
            buffer.append( it.next().formatIndentation( !it.hasNext() ) );
        }
        return buffer.toString();
    }

    private String formatNode( DependencyNode node )
    {
        return String.valueOf( node );
    }

    public boolean visitLeave( DependencyNode node )
    {
        if ( !childInfos.isEmpty() )
        {
            childInfos.remove( childInfos.size() - 1 );
        }
        if ( !childInfos.isEmpty() )
        {
            childInfos.get( childInfos.size() - 1 ).index++;
        }
        return true;
    }

    private static class ChildInfo
    {

        final int count;

        int index;

        public ChildInfo( int count )
        {
            this.count = count;
        }

        public String formatIndentation( boolean end )
        {
            boolean last = index + 1 >= count;
            if ( end )
            {
                return last ? "\\- " : "+- ";
            }
            return last ? "   " : "|  ";
        }

    }

}
