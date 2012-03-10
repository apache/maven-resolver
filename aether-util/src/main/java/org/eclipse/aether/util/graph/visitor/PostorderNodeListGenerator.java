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

import org.eclipse.aether.graph.DependencyNode;

/**
 * Generates a sequence of dependency nodes from a dependeny graph by traversing the graph in postorder. This visitor
 * visits each node exactly once regardless how many paths within the dependency graph lead to the node such that the
 * resulting node sequence is free of duplicates.
 */
public final class PostorderNodeListGenerator
    extends AbstractDepthFirstNodeListGenerator
{

    private final Stack<Boolean> visits;

    /**
     * Creates a new postorder list generator.
     */
    public PostorderNodeListGenerator()
    {
        visits = new Stack<Boolean>();
    }

    @Override
    public boolean visitEnter( DependencyNode node )
    {
        boolean visited = !setVisited( node );

        visits.push( Boolean.valueOf( visited ) );

        if ( visited )
        {
            return false;
        }

        return true;
    }

    @Override
    public boolean visitLeave( DependencyNode node )
    {
        Boolean visited = visits.pop();

        if ( visited.booleanValue() )
        {
            return true;
        }

        if ( node.getDependency() != null )
        {
            nodes.add( node );
        }

        return true;
    }

}
