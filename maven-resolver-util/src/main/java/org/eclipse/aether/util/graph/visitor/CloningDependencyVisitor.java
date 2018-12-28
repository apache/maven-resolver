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

import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that constructs a clone of the visited dependency graph. If such a visitor is passed into a
 * {@link FilteringDependencyVisitor}, a sub graph can be created. This class creates shallow clones of the visited
 * dependency nodes (via {@link DefaultDependencyNode#DefaultDependencyNode(DependencyNode)}) but clients can create a
 * subclass and override {@link #clone(DependencyNode)} to alter the clone process.
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
        parents = new Stack<>();
        clones = new IdentityHashMap<>( 256 );
    }

    /**
     * Gets the root node of the cloned dependency graph.
     * 
     * @return The root node of the cloned dependency graph or {@code null}.
     */
    public final DependencyNode getRootNode()
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
        return new DefaultDependencyNode( node );
    }

    public final boolean visitEnter( DependencyNode node )
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

    public final boolean visitLeave( DependencyNode node )
    {
        parents.pop();

        return true;
    }

}
