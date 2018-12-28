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

import java.util.IdentityHashMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that delegates to another visitor if a node hasn't been visited before. In other words, this
 * visitor provides a tree-view of a dependency graph which generally can have multiple paths to the same node or even
 * cycles.
 */
public final class TreeDependencyVisitor
    implements DependencyVisitor
{

    private final Map<DependencyNode, Object> visitedNodes;

    private final DependencyVisitor visitor;

    private final Stack<Boolean> visits;

    /**
     * Creates a new visitor that delegates to the specified visitor.
     *
     * @param visitor The visitor to delegate to, must not be {@code null}.
     */
    public TreeDependencyVisitor( DependencyVisitor visitor )
    {
        this.visitor = requireNonNull( visitor, "dependency visitor delegate cannot be null" );
        visitedNodes = new IdentityHashMap<>( 512 );
        visits = new Stack<>();
    }

    public boolean visitEnter( DependencyNode node )
    {
        boolean visited = visitedNodes.put( node, Boolean.TRUE ) != null;

        visits.push( visited );

        if ( visited )
        {
            return false;
        }

        return visitor.visitEnter( node );
    }

    public boolean visitLeave( DependencyNode node )
    {
        Boolean visited = visits.pop();

        if ( visited )
        {
            return true;
        }

        return visitor.visitLeave( node );
    }

}
