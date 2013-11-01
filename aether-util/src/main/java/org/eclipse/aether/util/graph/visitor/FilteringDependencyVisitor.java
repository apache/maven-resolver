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

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that delegates to another visitor if nodes match a filter. Note that in case of a mismatching
 * node, the children of that node are still visisted and presented to the filter.
 */
public final class FilteringDependencyVisitor
    implements DependencyVisitor
{

    private final DependencyFilter filter;

    private final DependencyVisitor visitor;

    private final Stack<Boolean> accepts;

    private final Stack<DependencyNode> parents;

    /**
     * Creates a new visitor that delegates traversal of nodes matching the given filter to the specified visitor.
     * 
     * @param visitor The visitor to delegate to, must not be {@code null}.
     * @param filter The filter to apply, may be {@code null} to not filter.
     */
    public FilteringDependencyVisitor( DependencyVisitor visitor, DependencyFilter filter )
    {
        if ( visitor == null )
        {
            throw new IllegalArgumentException( "dependency visitor not specified" );
        }
        this.visitor = visitor;
        this.filter = filter;
        this.accepts = new Stack<Boolean>();
        this.parents = new Stack<DependencyNode>();
    }

    /**
     * Gets the visitor to which this visitor delegates to.
     * 
     * @return The visitor being delegated to, never {@code null}.
     */
    public DependencyVisitor getVisitor()
    {
        return visitor;
    }

    /**
     * Gets the filter being applied before delegation.
     * 
     * @return The filter being applied or {@code null} if none.
     */
    public DependencyFilter getFilter()
    {
        return filter;
    }

    public boolean visitEnter( DependencyNode node )
    {
        boolean accept = filter == null || filter.accept( node, parents );

        accepts.push( accept );

        parents.push( node );

        if ( accept )
        {
            return visitor.visitEnter( node );
        }
        else
        {
            return true;
        }
    }

    public boolean visitLeave( DependencyNode node )
    {
        parents.pop();

        Boolean accept = accepts.pop();

        if ( accept )
        {
            return visitor.visitLeave( node );
        }
        else
        {
            return true;
        }
    }

}
