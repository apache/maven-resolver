package org.eclipse.aether.util.graph.visitor;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that records all paths leading to nodes matching a certain filter criteria.
 */
public final class PathRecordingDependencyVisitor
    implements DependencyVisitor
{

    private final DependencyFilter filter;

    private final List<List<DependencyNode>> paths;

    private final Stack<DependencyNode> parents;

    private final Map<DependencyNode, Object> visited;

    private final boolean excludeChildrenOfMatches;

    /**
     * Creates a new visitor that uses the specified filter to identify terminal nodes of interesting paths. The visitor
     * will not search for paths going beyond an already matched node.
     * 
     * @param filter The filter used to select terminal nodes of paths to record, may be {@code null} to match any node.
     */
    public PathRecordingDependencyVisitor( DependencyFilter filter )
    {
        this( filter, true );
    }

    /**
     * Creates a new visitor that uses the specified filter to identify terminal nodes of interesting paths.
     * 
     * @param filter The filter used to select terminal nodes of paths to record, may be {@code null} to match any node.
     * @param excludeChildrenOfMatches Flag controlling whether children of matched nodes should be excluded from the
     *            traversal, thereby ignoring any potential paths to other matching nodes beneath a matching ancestor
     *            node. If {@code true}, all recorded paths will have only one matching node (namely the terminal node),
     *            if {@code false} a recorded path can consist of multiple matching nodes.
     */
    public PathRecordingDependencyVisitor( DependencyFilter filter, boolean excludeChildrenOfMatches )
    {
        this.filter = filter;
        this.excludeChildrenOfMatches = excludeChildrenOfMatches;
        paths = new ArrayList<>();
        parents = new Stack<>();
        visited = new IdentityHashMap<>( 128 );
    }

    /**
     * Gets the filter being used to select terminal nodes.
     * 
     * @return The filter being used or {@code null} if none.
     */
    public DependencyFilter getFilter()
    {
        return filter;
    }

    /**
     * Gets the paths leading to nodes matching the filter that have been recorded during the graph visit. A path is
     * given as a sequence of nodes, starting with the root node of the graph and ending with a node that matched the
     * filter.
     * 
     * @return The recorded paths, never {@code null}.
     */
    public List<List<DependencyNode>> getPaths()
    {
        return paths;
    }

    public boolean visitEnter( DependencyNode node )
    {
        boolean accept = filter == null || filter.accept( node, parents );

        parents.push( node );

        if ( accept )
        {
            DependencyNode[] path = new DependencyNode[parents.size()];
            for ( int i = 0, n = parents.size(); i < n; i++ )
            {
                path[n - i - 1] = parents.get( i );
            }
            paths.add( Arrays.asList( path ) );

            if ( excludeChildrenOfMatches )
            {
                return false;
            }
        }

        if ( visited.put( node, Boolean.TRUE ) != null )
        {
            return false;
        }

        return true;
    }

    public boolean visitLeave( DependencyNode node )
    {
        parents.pop();
        visited.remove( node );

        return true;
    }

}
