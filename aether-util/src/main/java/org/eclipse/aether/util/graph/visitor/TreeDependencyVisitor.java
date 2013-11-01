/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.visitor;

import java.util.IdentityHashMap;
import java.util.Map;

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
        if ( visitor == null )
        {
            throw new IllegalArgumentException( "no visitor delegate specified" );
        }
        this.visitor = visitor;
        visitedNodes = new IdentityHashMap<DependencyNode, Object>( 512 );
        visits = new Stack<Boolean>();
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
