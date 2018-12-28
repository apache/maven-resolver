package org.eclipse.aether.internal.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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

        LinkedList<Indent> indents = new LinkedList<>();
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
            this.lines = new ArrayList<>();
            this.nodes = new IdentityHashMap<>( 1024 );
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
