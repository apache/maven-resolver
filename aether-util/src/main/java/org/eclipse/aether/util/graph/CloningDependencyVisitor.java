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
package org.eclipse.aether.util.graph;

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.DefaultDependencyNode;
import org.eclipse.aether.util.graph.FilteringDependencyVisitor;

/**
 * A dependency visitor that constructs a clone of the visited dependency graph. If such a visitor is passed into a
 * {@link FilteringDependencyVisitor}, a sub graph can be created. This class creates shallow clones of the visited
 * dependency nodes but clients can create a subclass and override {@link #clone(DependencyNode)} to alter the clone
 * process.
 */
public class CloningDependencyVisitor
    implements DependencyVisitor
{

    private final Map<DependencyNode, DependencyNode> clones;

    private final Stack<DependencyNode> parents;

    private DependencyNode root;

    /**
     * Creates a new visitor that clones the visited nodes.
     */
    public CloningDependencyVisitor()
    {
        parents = new Stack<DependencyNode>();
        clones = new IdentityHashMap<DependencyNode, DependencyNode>( 256 );
    }

    /**
     * Gets the root node of the cloned dependency graph.
     * 
     * @return The root node of the cloned dependency graph or {@code null}.
     */
    public DependencyNode getRootNode()
    {
        return root;
    }

    /**
     * Creates a clone of the specified node.
     * 
     * @param node The node to clone, must not be {@code null}.
     * @return The cloned node, never {@code null}.
     */
    protected DependencyNode clone( DependencyNode node )
    {
        DefaultDependencyNode clone = new DefaultDependencyNode( node );
        return clone;
    }

    public boolean visitEnter( DependencyNode node )
    {
        boolean recurse = true;

        DependencyNode clone = clones.get( node );
        if ( clone == null )
        {
            clone = clone( node );
            clones.put( node, clone );
        }
        else
        {
            recurse = false;
        }

        DependencyNode parent = parents.peek();

        if ( parent == null )
        {
            root = clone;
        }
        else
        {
            parent.getChildren().add( clone );
        }

        parents.push( clone );

        return recurse;
    }

    public boolean visitLeave( DependencyNode node )
    {
        parents.pop();

        return true;
    }

}
