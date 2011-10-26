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
package org.eclipse.aether.internal.impl;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A helper to visualize dependency graphs.
 */
public class DependencyGraphDumper
{

    public static void dump( PrintWriter writer, DependencyNode node )
    {
        Context context = new Context();
        dump( context, node, 0, true );

        LinkedList<Indent> indents = new LinkedList<Indent>();
        for ( Line line : context.lines )
        {
            if ( line.depth > indents.size() )
            {
                if ( !indents.isEmpty() )
                {
                    if ( indents.getLast() == Indent.CHILD )
                    {
                        indents.removeLast();
                        indents.addLast( Indent.CHILDREN );
                    }
                    else if ( indents.getLast() == Indent.LAST_CHILD )
                    {
                        indents.removeLast();
                        indents.addLast( Indent.NO_CHILDREN );
                    }
                }
                indents.addLast( line.last ? Indent.LAST_CHILD : Indent.CHILD );
            }
            else if ( line.depth < indents.size() )
            {
                while ( line.depth <= indents.size() )
                {
                    indents.removeLast();
                }
                indents.addLast( line.last ? Indent.LAST_CHILD : Indent.CHILD );
            }
            else if ( line.last && !indents.isEmpty() )
            {
                indents.removeLast();
                indents.addLast( Indent.LAST_CHILD );
            }

            for ( Indent indent : indents )
            {
                writer.print( indent );
            }

            line.print( writer );
        }

        writer.flush();
    }

    private static void dump( Context context, DependencyNode node, int depth, boolean last )
    {
        Line line = context.nodes.get( node );
        if ( line != null )
        {
            if ( line.id <= 0 )
            {
                line.id = ++context.ids;
            }
            context.lines.add( new Line( null, line.id, depth, last ) );
            return;
        }

        Dependency dependency = node.getDependency();

        if ( dependency == null )
        {
            line = new Line( null, 0, depth, last );
        }
        else
        {
            line = new Line( dependency, 0, depth, last );
        }

        context.lines.add( line );

        context.nodes.put( node, line );

        depth++;

        for ( Iterator<DependencyNode> it = node.getChildren().iterator(); it.hasNext(); )
        {
            DependencyNode child = it.next();
            dump( context, child, depth, !it.hasNext() );
        }
    }

    static enum Indent
    {

        NO_CHILDREN( "   " ),

        CHILDREN( "|  " ),

        CHILD( "+- " ),

        LAST_CHILD( "\\- " );

        private final String chars;

        Indent( String chars )
        {
            this.chars = chars;
        }

        @Override
        public String toString()
        {
            return chars;
        }

    }

    static class Context
    {

        int ids;

        List<Line> lines;

        Map<DependencyNode, Line> nodes;

        Context()
        {
            this.lines = new ArrayList<Line>();
            this.nodes = new IdentityHashMap<DependencyNode, Line>( 1024 );
        }

    }

    static class Line
    {

        Dependency dependency;

        int id;

        int depth;

        boolean last;

        Line( Dependency dependency, int id, int depth, boolean last )
        {
            this.dependency = dependency;
            this.id = id;
            this.depth = depth;
            this.last = last;
        }

        void print( PrintWriter writer )
        {
            if ( dependency == null )
            {
                if ( id > 0 )
                {
                    writer.print( "^" );
                    writer.print( id );
                }
                else
                {
                    writer.print( "(null)" );
                }
            }
            else
            {
                if ( id > 0 )
                {
                    writer.print( "(" );
                    writer.print( id );
                    writer.print( ")" );
                }
                writer.print( dependency.getArtifact() );
                if ( dependency.getScope().length() > 0 )
                {
                    writer.print( ":" );
                    writer.print( dependency.getScope() );
                }
            }
            writer.println();
        }

    }

}
